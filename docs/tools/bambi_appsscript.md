[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [BAMBI Sheet Interface Setup](#)

# Getting Started with the BAMBI Sheet Interface

This guide provides instructions on how to set up a Google Sheet to communicate with the BAMBI backend service.

## Overview

The setup involves two Google Apps Scripts that work together:

1.  **BAMBISheetsInterface**: This script is bound directly to your Google Sheet. It creates the necessary tabs, formatting, and custom menu options you'll use to interact with BAMBI.
2.  **PubSubPublisherWebApp**: This is a standalone "web app" script that publishes messages to the BAMBI backend using Google's Pub/Sub service.

We use two separate scripts because a script must be linked to a Google Cloud Platform (GCP) project to get permission to publish messages. By having one central `PubSubPublisherWebApp`, multiple users can create their own `BAMBISheetsInterface` sheets without needing complex GCP configurations.

## Creating Your BAMBI Sheet

Follow these instructions to create your own copy of the BAMBI sheet. 

### 🚀 Automated Setup

This method uses a Python script to create and configure your new BAMBI sheet automatically.


#### **One-Time Prerequisites**

Before running the script for the first time, you must enable the Apps Script API.
If you do not want to enable the Apps Script API in your project, you can jump to the Manual Setup.

1.  **Enable the API in your GCP Project**: Visit the following URL and enable the API. Replace `${GCP_PROJECT_ID}` with your actual Google Cloud Project ID.
    * `https://console.developers.google.com/apis/api/script.googleapis.com/overview?project=${GCP_PROJECT_ID}`
2.  **Enable the API for your Google Account**: Visit your Apps Script settings and turn on the "Google Apps Script API."
    * `https://script.google.com/home/usersettings`

#### **Instructions**

1.  **Authenticate with Google Cloud**: Run the following commands in your terminal to log in and set the correct permissions.

    ```shell
    gcloud auth login
    gcloud config set project ${GCP_PROJECT_ID}
    gcloud auth application-default login \
    --scopes="openid,[https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets,https://www.googleapis.com/auth/script.projects,https://www.googleapis.com/auth/drive.metadata.readonly](https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets,https://www.googleapis.com/auth/script.projects,https://www.googleapis.com/auth/drive.metadata.readonly)"
    ```

2.  **Run the Creation Script**: Execute the Python script to create the sheet. Replace `${UDMI_ROOT}` with the path to your UDMI root directory and provide a title for your new sheet.

    ```shell
    # Activate your virtual environment 
    source ${UDMI_ROOT}/venv/bin/activate

    # Run the script
    python ${UDMI_ROOT}/misc/bambi_appscript/create_bambi_sheet.py --sheet_title "Your New Sheet Title"
    ```
✅ Once the script finishes, you should see the BAMBI menu ready as well as input tabs populated on your sheet.

**Remember to share it with the service account used by the backend service!** If not shared, the backend will not be able to read from and write to the sheet.
    

### 🔧 Manual Setup

Follow these steps if you prefer to configure the sheet manually or if you do not want to enable the Apps Script API in your GCP project.

1.  **Create a new Google Sheet**: Go to [sheets.new](https://sheets.new).
2.  **Open Apps Script**: From the menu, navigate to **Extensions > Apps Script**.
3.  **Show Manifest File**: In the Apps Script editor, go to **Project Settings** (the ⚙️ icon) and check the box for **"Show appsscript.json manifest file in editor"**.
4.  **Copy Script Files**: Go back to the **Editor** (the `<>` icon). Replicate all the files from the `${UDMI_ROOT}/misc/bambi_appsscript/bambi_sheet` directory into your Apps Script project.
    * *If you deployed a custom Pub/Sub web app (see advanced section), remember to update the `PUBSUB_WEB_APP_URL` in the `Constants.gs` file with your custom URL.*
5.  **Refresh the Sheet**: Return to your Google Sheet tab and refresh the page.

✅ The **BAMBI** menu should now appear, and your sheet is ready with the input sheets. 

**Remember to share it with the service account used by the backend service!** If not shared, the backend will not be able to read from and write to the sheet.

---

## Advanced: Deploying a Custom Pub/Sub Web App

This section is **optional**. You only need to follow these steps if you cannot use the pre-deployed `PubSubPublisherWebApp` and/or need to host your own.

1.  **Authenticate with Google Cloud**:
    ```shell
    gcloud auth login
    gcloud config set project ${GCP_PROJECT_ID}
    gcloud auth application-default login
    ```

2.  **Create a New Apps Script**: Go to [script.new](https://script.new).

3.  **Link to GCP Project**:
    * Go to **Project Settings** ⚙️.
    * Under **Google Cloud Platform (GCP) Project**, click **Change Project**.
    * Enter the Project Number of your standard GCP project. You may need to enable the **Pub/Sub API** in that project first.

4.  **Update Script Files**:
    * In **Project Settings**, check the box for **"Show appsscript.json manifest file in editor"**.
    * Return to the **Editor** `<>`.
    * Copy the content from `${UDMI_ROOT}/misc/bambi_appsscript/deps/PubSubPublisherWebApp/appsscript.json` into your `appsscript.json` file.
    * Copy the content from `${UDMI_ROOT}/misc/bambi_appsscript/deps/PubSubPublisherWebApp/Code.gs` into your `Code.gs` file.

5.  **Deploy the Web App**:
    * Click the **Deploy** button and select **New Deployment**.
    * From the **Select type** gear icon, choose **Web app**.
    * Provide a description (e.g., "v1").
    * Configure the authorization settings as needed.
    * Click **Deploy**.

6.  **Save the Web App URL**: After deployment, copy the generated **Web app URL**. You will need this URL for the `Constants.gs` file in any `BAMBISheetsInterface` script that uses this custom publisher.

