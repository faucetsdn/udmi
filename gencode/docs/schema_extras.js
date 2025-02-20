/**
 *  Dynamically remove `.md` extension from links when schema browser is viewed
 *  on a hosted source. This is targeted at Github pages, which only shows the
 *  rendered MD files when the `.md` extension is omitted (otherwise raw file is
 *  presented). A dynamic rewrite ensures all the links in the schema files are
 *  valid, but pretty when clicked online
 */
function removedHostedMDExtensions(){
    const docsRegex = '^(https.*).md(#.*)?$';
    if (window.location.href.match("https://")) {
        var links = document.getElementsByTagName("a");
        for (var link of links){
            var match = link.href.match(docsRegex)
            if(match) {
                var anchor = (match[2] === undefined ? '' : match[2]);
                link.href = `${match[1]}${anchor}`
            }
        }
    }
}
