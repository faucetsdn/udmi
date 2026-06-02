// --- Global UI State ---
let activeStage = 'collect_stats'; // default stage
let activeRunId = null;
let sseConnection = null;
let activeFilePath = null;
let activeFileContent = "";

// --- Initialize on Document Load ---
document.addEventListener('DOMContentLoaded', () => {
    // Initialize Lucide Icons
    lucide.createIcons();

    // Setup Markdown parser settings
    marked.setOptions({
        gfm: true,
        breaks: true,
        highlight: function(code, lang) {
            const language = hljs.getLanguage(lang) ? lang : 'plaintext';
            return hljs.highlight(code, { language }).value;
        }
    });

    // Bind event listeners for all form fields to update CLI command preview
    setupCommandPreviewListeners();

    // Load Initial File Tree
    refreshFileTree();

    // Initial preview generation
    updateCommandPreview();
});

// --- Sidebar Tab Switching ---
function switchSidebarTab(tabId) {
    document.querySelectorAll('.sidebar .tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.sidebar .tab-content').forEach(content => content.classList.add('hidden'));
    
    // Find clicked tab button and activate it
    const activeBtn = Array.from(document.querySelectorAll('.sidebar .tab-btn')).find(btn => btn.getAttribute('onclick').includes(tabId));
    if (activeBtn) activeBtn.classList.add('active');
    
    const targetContent = document.getElementById(`sidebar-tab-${tabId}`);
    if (targetContent) targetContent.classList.remove('hidden');
}

// --- Stage Navigation & Config updates ---
function selectStage(stageId) {
    activeStage = stageId;
    
    // Update top header pipeline step UI
    document.querySelectorAll('.pipeline-step').forEach(step => step.classList.remove('active'));
    
    let stepElem = null;
    if (stageId === 'collect_stats') stepElem = document.getElementById('step-collect');
    else if (stageId === 'evaluate_stability') stepElem = document.getElementById('step-stability');
    else if (stageId === 'diagnose') stepElem = document.getElementById('step-diagnose');
    
    if (stepElem) stepElem.classList.add('active');

    // Update form views in the sidebar Controls tab
    document.querySelectorAll('.tool-form').forEach(form => form.classList.remove('active'));
    const activeForm = document.getElementById(`form-${stageId}`);
    if (activeForm) activeForm.classList.add('active');

    // Switch to Controls tab
    switchSidebarTab('trigger');

    // Refresh CLI command preview
    updateCommandPreview();
}

// --- Command Line Preview Logic ---
function setupCommandPreviewListeners() {
    const inputs = document.querySelectorAll('.tool-form input, .tool-form select');
    inputs.forEach(input => {
        input.addEventListener('input', updateCommandPreview);
        input.addEventListener('change', updateCommandPreview);
    });
}

function generateCommandArray() {
    let cmdArray = [];
    
    if (activeStage === 'collect_stats') {
        const target = document.getElementById('collect-target').value || '//mqtt/localhost';
        const local = document.getElementById('collect-local').checked;
        const iterations = document.getElementById('collect-iterations').value;
        const suite = document.getElementById('collect-suite').value;
        const tests = document.getElementById('collect-tests').value.trim();
        const verbose = document.getElementById('collect-verbose').checked;

        cmdArray.push('--target', target);
        if (local) cmdArray.push('--local');
        if (iterations) cmdArray.push('--iterations', iterations);
        if (suite !== 'both') cmdArray.push('--suite', suite);
        if (tests) cmdArray.push('--tests', tests);
        if (verbose) cmdArray.push('--verbose');

    } else if (activeStage === 'evaluate_stability') {
        const target = document.getElementById('stability-target').value || '//mqtt/localhost';
        const phase = document.getElementById('stability-phase').value;
        const bundles = document.getElementById('stability-bundles').value;

        cmdArray.push('--target', target);
        if (phase) cmdArray.push('--phase', phase);
        if (bundles) cmdArray.push('--bundles-dir', bundles);

    } else if (activeStage === 'diagnose') {
        const target = document.getElementById('diagnose-target').value || '//mqtt/localhost';
        const bundlesDir = document.getElementById('diagnose-bundles-dir').value;

        cmdArray.push('--target', target);
        if (bundlesDir) cmdArray.push('--bundles-dir', bundlesDir);
    }

    return cmdArray;
}

function updateCommandPreview() {
    const preview = document.getElementById('command-preview');
    const cmdArray = generateCommandArray();
    preview.innerText = `mantis/bin/${activeStage} ` + cmdArray.join(' ');
}

// --- Asynchronous Process Triggering & Stream Logs ---
async function triggerToolExecution() {
    const btnExecute = document.getElementById('btn-execute');
    const systemStatus = document.getElementById('system-status');
    const btnKill = document.getElementById('btn-kill');
    
    // Disable execute button and update status UI
    btnExecute.disabled = true;
    btnExecute.innerHTML = `<i data-lucide="loader" class="icon-spin"></i> Running Stage...`;
    systemStatus.className = "status-pill running";
    systemStatus.querySelector('.status-text').innerText = `Running ${activeStage}...`;
    btnKill.classList.remove('hidden');
    
    // Clear logs and display command starting message
    clearTerminal();
    const commandPreviewStr = document.getElementById('command-preview').innerText;
    appendTerminalLine(`[SYSTEM] Initializing Stage Execution: ${activeStage}`, 'system-msg');
    appendTerminalLine(`[SYSTEM] Running Command: ${commandPreviewStr}\n`, 'system-msg');

    // Gather parameters
    const cmdOptions = generateCommandArray();
    const payload = {
        tool: activeStage,
        options: cmdOptions
    };

    // If Gemini API Key is provided in the form, we could append it. We also check environment.
    if (activeStage === 'diagnose') {
        const key = document.getElementById('gemini-key').value;
        if (key) {
            // The server can optionally read key. (Typically loaded from server host env)
            appendTerminalLine(`[SYSTEM] Custom Gemini API Key provided. Passing to execution context...`, 'system-msg');
        }
    }

    try {
        const response = await fetch('/api/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (!data.success) {
            throw new Error(data.error || 'Failed to trigger backend runner process');
        }

        activeRunId = data.run_id;
        
        // Set up Server-Sent Events to stream output logs
        connectLogsStream(activeRunId);

    } catch (err) {
        appendTerminalLine(`\n[CRITICAL ERROR] Trigger Failed: ${err.message}`, 'log-error');
        resetSystemStatus("failed");
    }
}

function connectLogsStream(runId) {
    if (sseConnection) {
        sseConnection.close();
    }

    sseConnection = new EventSource(`/api/stream?run_id=${runId}`);

    sseConnection.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            
            // Check for line updates
            if (data.log !== undefined) {
                // Check format characteristics and color code lines
                let logClass = '';
                const logText = data.log;
                
                if (logText.includes('[ERROR]') || logText.toLowerCase().includes('exception') || logText.toLowerCase().includes('failed')) {
                    logClass = 'log-error';
                } else if (logText.includes('[WARNING]') || logText.includes('[WARN]')) {
                    logClass = 'log-warning';
                } else if (logText.includes('[SUCCESS]') || logText.includes('Stabilization evaluation complete') || logText.includes('Triage complete')) {
                    logClass = 'log-success';
                }

                appendTerminalLine(logText, logClass);
            }

            // Check for status updates
            if (data.status !== undefined) {
                sseConnection.close();
                resetSystemStatus(data.status);
                
                // Automatically refresh tree view since new files were likely generated!
                appendTerminalLine(`\n[SYSTEM] Stage completed with status: ${data.status}. Refreshing out/ explorer.`, 'system-msg');
                refreshFileTree();
                switchSidebarTab('files');
            }

        } catch (e) {
            console.error("Error parsing log stream event data:", e);
        }
    };

    sseConnection.onerror = (err) => {
        console.error("EventSource SSE log stream failed:", err);
        appendTerminalLine(`\n[SYSTEM WARNING] Logs stream closed or lost connection. Checking status...`, 'log-warning');
        sseConnection.close();
        checkFinalProcessStatus(runId);
    };
}

async function checkFinalProcessStatus(runId) {
    try {
        const res = await fetch(`/api/status?run_id=${runId}`);
        const data = await res.json();
        if (data.status !== "running") {
            resetSystemStatus(data.status);
            refreshFileTree();
        }
    } catch (e) {
        resetSystemStatus("unknown");
    }
}

async function killActiveSubprocess() {
    if (!activeRunId) return;
    
    appendTerminalLine(`\n[SYSTEM] Terminating active process: ${activeRunId}...`, 'log-warning');
    try {
        await fetch('/api/kill', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ run_id: activeRunId })
        });
    } catch (e) {
        appendTerminalLine(`[SYSTEM ERROR] Failed to send terminate request: ${e.message}`, 'log-error');
    }
}

function resetSystemStatus(status) {
    const btnExecute = document.getElementById('btn-execute');
    const systemStatus = document.getElementById('system-status');
    const btnKill = document.getElementById('btn-kill');

    btnExecute.disabled = false;
    btnExecute.innerHTML = `<i data-lucide="play"></i> Execute Stage`;
    btnKill.classList.add('hidden');
    
    if (status === "completed") {
        systemStatus.className = "status-pill ready";
        systemStatus.querySelector('.status-text').innerText = "System Ready";
    } else {
        systemStatus.className = "status-pill failed";
        systemStatus.querySelector('.status-text').innerText = `Exit: ${status}`;
    }

    // Refresh Lucide icons
    lucide.createIcons();
}

// --- Terminal UI Management ---
function appendTerminalLine(text, className = '') {
    const termLogs = document.getElementById('terminal-logs');
    const cursor = termLogs.querySelector('.term-cursor');
    
    const line = document.createElement('div');
    line.className = `term-line ${className}`;
    line.innerText = text;

    // Insert before terminal cursor
    termLogs.insertBefore(line, cursor);

    // Autoscroll if active
    if (document.getElementById('term-autoscroll').checked) {
        termLogs.scrollTop = termLogs.scrollHeight;
    }
}

function clearTerminal() {
    const termLogs = document.getElementById('terminal-logs');
    const cursor = termLogs.querySelector('.term-cursor');
    
    // Remove everything except cursor
    termLogs.innerHTML = '';
    termLogs.appendChild(cursor);
}

function copyTerminalLogs() {
    const termLogs = document.getElementById('terminal-logs');
    const lines = Array.from(termLogs.querySelectorAll('.term-line')).map(line => line.innerText);
    const text = lines.join('\n');
    navigator.clipboard.writeText(text).then(() => {
        alert("Console logs copied to clipboard!");
    });
}

// --- Folder Explorer Tree hydrate & navigation ---
async function refreshFileTree() {
    const treeRoot = document.getElementById('file-tree-root');
    treeRoot.innerHTML = '<div class="tree-placeholder"><i data-lucide="loader" class="icon-spin"></i> Scanning out/ directory...</div>';
    lucide.createIcons();

    try {
        const response = await fetch('/api/files');
        if (!response.ok) throw new Error('HTTP error fetching file structure');
        const tree = await response.json();
        
        treeRoot.innerHTML = '';
        if (!tree.children || tree.children.length === 0) {
            treeRoot.innerHTML = '<div class="tree-placeholder">No generated files found under out/ yet. Run a Mantis stage tool to produce outputs!</div>';
            return;
        }

        // Recursively build tree HTML
        buildTreeNodeHTML(treeRoot, tree, true);
        lucide.createIcons();

    } catch (e) {
        treeRoot.innerHTML = `<div class="tree-placeholder text-danger">Failed to load folder tree: ${e.message}</div>`;
    }
}

function buildTreeNodeHTML(container, node, isRoot = false) {
    // If root directory, skip creating element for root container itself and just create children
    if (isRoot) {
        node.children.forEach(child => {
            const childContainer = document.createElement('div');
            childContainer.className = 'tree-node';
            container.appendChild(childContainer);
            buildTreeNodeHTML(childContainer, child);
        });
        return;
    }

    // Create a node row
    const row = document.createElement('div');
    row.className = 'tree-row';
    
    // Check file vs directory icons
    let iconName = 'file';
    if (node.type === 'directory') {
        iconName = 'folder';
    } else if (node.name.endsWith('.md')) {
        iconName = 'file-text';
    } else if (node.name.endsWith('.json') || node.name.endsWith('.yaml') || node.name.endsWith('.yml')) {
        iconName = 'braces';
    } else if (node.name.endsWith('.log')) {
        iconName = 'terminal';
    }

    const isMarkdown = node.name.endsWith('.md');
    const iconClass = `tree-icon ${node.type} ${isMarkdown ? 'markdown' : ''}`;
    
    let chevronHTML = '';
    if (node.type === 'directory') {
        chevronHTML = `<i data-lucide="chevron-right" class="tree-chevron expanded"></i>`;
    } else {
        chevronHTML = `<span style="width:16px; display:inline-block"></span>`;
    }

    row.innerHTML = `
        ${chevronHTML}
        <i data-lucide="${iconName}" class="${iconClass}"></i>
        <span class="tree-node-name">${node.name}</span>
    `;
    
    container.appendChild(row);

    // Expand / Collapse behaviour
    if (node.type === 'directory') {
        const childrenContainer = document.createElement('div');
        childrenContainer.className = 'tree-node-children';
        container.appendChild(childrenContainer);

        // Chevron trigger
        const chevron = row.querySelector('.tree-chevron');
        
        row.addEventListener('click', (e) => {
            // If clicking directory, toggle child visibility
            const isCollapsed = childrenContainer.classList.toggle('hidden');
            if (isCollapsed) {
                chevron.classList.remove('expanded');
            } else {
                chevron.classList.add('expanded');
            }
        });

        // Hydrate children
        node.children.forEach(child => {
            const childNodeContainer = document.createElement('div');
            childNodeContainer.className = 'tree-node';
            childrenContainer.appendChild(childNodeContainer);
            buildTreeNodeHTML(childNodeContainer, child);
        });
    } else {
        // If file, click loads content
        row.addEventListener('click', () => {
            // Activate active highlight status
            document.querySelectorAll('.tree-row').forEach(r => r.classList.remove('active'));
            row.classList.add('active');
            
            loadAndRenderFile(node.path, node.name);
        });
    }
}

// --- Content Renders & Viewers ---
async function loadAndRenderFile(relPath, fileName) {
    const viewerPane = document.getElementById('viewer-content-pane');
    const viewerTitle = document.getElementById('viewer-file-name');
    const viewerPath = document.getElementById('viewer-file-path');
    const btnCopy = document.getElementById('btn-copy-viewer');

    viewerPane.innerHTML = '<div class="viewer-welcome"><i data-lucide="loader" class="welcome-icon icon-spin"></i><h3>Retrieving file content...</h3></div>';
    viewerTitle.innerText = fileName;
    viewerPath.innerText = relPath;
    lucide.createIcons();

    try {
        const response = await fetch(`/api/file-content?path=${encodeURIComponent(relPath)}`);
        if (!response.ok) throw new Error('Failed to download file contents');
        
        const text = await response.text();
        activeFileContent = text;
        activeFilePath = relPath;
        btnCopy.classList.remove('hidden');

        const isMarkdown = fileName.endsWith('.md');
        const isYaml = fileName.endsWith('.yaml') || fileName.endsWith('.yml');
        const isJson = fileName.endsWith('.json');
        
        if (isMarkdown) {
            // Renders parsed Markdown
            let html = marked.parse(text);
            
            // Let's enrich markdown parsing to parse custom GitHub Alert notations:
            // Replace `> [!NOTE]` blockquotes with stylized alerts
            html = enrichMarkdownAlertStyles(html);
            
            viewerPane.innerHTML = `<div class="rendered-markdown">${html}</div>`;
            
            // Trigger Highlight.js for any code blocks in rendered markdown
            viewerPane.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });

        } else if (isYaml || isJson) {
            // Renders syntax-highlighted YAML/JSON
            const languageClass = isYaml ? 'language-yaml' : 'language-json';
            viewerPane.innerHTML = `
                <pre><code class="${languageClass}">${escapeHTML(text)}</code></pre>
            `;
            hljs.highlightElement(viewerPane.querySelector('code'));
        } else {
            // Render as plain text log
            viewerPane.innerHTML = `
                <pre><code class="language-plaintext">${escapeHTML(text)}</code></pre>
            `;
            hljs.highlightElement(viewerPane.querySelector('code'));
        }

    } catch (e) {
        viewerPane.innerHTML = `
            <div class="viewer-welcome text-danger">
                <i data-lucide="alert-circle" class="welcome-icon"></i>
                <h3>Failed to render file</h3>
                <p>${e.message}</p>
            </div>
        `;
        btnCopy.classList.add('hidden');
        lucide.createIcons();
    }
}

// Rich custom alert styling processor for GitHub alerts:
function enrichMarkdownAlertStyles(html) {
    // GitHub blockquotes have elements like <blockquote><p>[!NOTE]
    // We replace these with high-end styling container div panels
    
    // Replace NOTE alerts
    html = html.replace(
        /<blockquote>([\s\S]*?)\[!NOTE\]([\s\S]*?)<\/blockquote>/gi, 
        '<blockquote class="alert-note"><strong><i data-lucide="info" style="display:inline-block; width:14px; height:14px; vertical-align:text-bottom; margin-right:6px"></i> NOTE:</strong>$2</blockquote>'
    );

    // Replace TIP alerts
    html = html.replace(
        /<blockquote>([\s\S]*?)\[!TIP\]([\s\S]*?)<\/blockquote>/gi, 
        '<blockquote class="alert-tip" style="border-left-color:var(--primary); background:var(--primary-soft)"><strong><i data-lucide="sparkles" style="display:inline-block; width:14px; height:14px; vertical-align:text-bottom; margin-right:6px; color:var(--primary)"></i> TIP:</strong>$2</blockquote>'
    );

    // Replace IMPORTANT alerts
    html = html.replace(
        /<blockquote>([\s\S]*?)\[!IMPORTANT\]([\s\S]*?)<\/blockquote>/gi, 
        '<blockquote class="alert-important" style="border-left-color:var(--danger); background:var(--danger-soft)"><strong><i data-lucide="alert-triangle" style="display:inline-block; width:14px; height:14px; vertical-align:text-bottom; margin-right:6px; color:var(--danger)"></i> IMPORTANT:</strong>$2</blockquote>'
    );

    // Replace WARNING alerts
    html = html.replace(
        /<blockquote>([\s\S]*?)\[!WARNING\]([\s\S]*?)<\/blockquote>/gi, 
        '<blockquote class="alert-warning" style="border-left-color:var(--warning); background:var(--warning-soft)"><strong><i data-lucide="alert-circle" style="display:inline-block; width:14px; height:14px; vertical-align:text-bottom; margin-right:6px; color:var(--warning)"></i> WARNING:</strong>$2</blockquote>'
    );

    return html;
}

function copyViewerContent() {
    if (!activeFileContent) return;
    navigator.clipboard.writeText(activeFileContent).then(() => {
        alert("File content copied to clipboard!");
    });
}

function autoPopulateLatestBundle(tool) {
    // Auto discover latest directories and populate the forms
    appendTerminalLine(`[SYSTEM] Auto-detecting latest generated bundles/runs in out/mantis/test_bundles/...`, 'system-msg');
    
    fetch('/api/files')
        .then(res => res.json())
        .then(tree => {
            // Locate the test_bundles node
            let testBundlesNode = tree.children.find(n => n.name === "test_bundles");
            if (!testBundlesNode || !testBundlesNode.children || testBundlesNode.children.length === 0) {
                alert("No test bundles found under out/mantis/test_bundles/ yet. Please run 'Collect Stats' first!");
                return;
            }
            
            // Sort bundle folders (e.g., ci_search_20260601_140735 or local_loop_...) to find the newest by name pattern
            let bundles = [...testBundlesNode.children];
            if (bundles.length === 0) {
                alert("No bundle run directories found under test_bundles.");
                return;
            }
            
            // Sort descending to get the latest one
            bundles.sort((a, b) => b.name.localeCompare(a.name, undefined, { numeric: true, sensitivity: 'base' }));
            const latestBundle = bundles[0];
            const pathStr = `out/mantis/test_bundles/${latestBundle.name}/`;

            if (tool === 'stability') {
                document.getElementById('stability-bundles').value = pathStr;
                appendTerminalLine(`[SYSTEM] Populated bundles-dir for Stability Evaluator: ${pathStr}`, 'system-msg');
                updateCommandPreview();
            } else if (tool === 'diagnose') {
                document.getElementById('diagnose-bundles-dir').value = pathStr;
                appendTerminalLine(`[SYSTEM] Populated bundles-dir for AI Diagnostics: ${pathStr}`, 'system-msg');
                updateCommandPreview();
            }
        })
        .catch(err => {
            alert("Failed to auto-populate: out/ folder is empty or backend server returned error.");
            console.error("Error auto-populating latest bundle:", err);
        });
}

// --- Helpers ---
function escapeHTML(str) {
    return str.replace(/[&<>'"]/g, 
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}
