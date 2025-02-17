[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Stream Output to Google Sheets](#)

# Streaming Tool Output to Google Sheets

This document explains how to stream real-time output from any UDMI tool
directly into a Google Sheet. This is useful for monitoring, logging, and
analyzing data produced by UDMI tools.

### Prerequisites

* A Google account and a Google Sheet.
* The `gcloud` command-line tool installed and configured.

### Steps

1. **Grant Editor Access:** Ensure the user or service account running the
   streaming utility has **Editor** access to the target Google Sheet. This is
   essential for the utility to write data to the sheet.

2. **Authentication:** The `stream_to_gsheets` utility uses Google Cloud SDK (
   gcloud) application default credentials for authentication. Choose one of the
   following authentication methods:

    * **Human User (Interactive):**  If you are running the utility as yourself,
      authenticate using the following commands. These commands will open a
      browser window and prompt you to log in with your Google account.

      ```shell
      gcloud auth login
      gcloud auth application-default login \
      --scopes="openid,https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets"
      ```

    * **Service Account (Non-Interactive):** If you are running the utility from
      a server or automated process, you should use a service account.
      If you want to use a service account, activate it using:

      ```shell
      gcloud auth activate-service-account --key-file=/path/to/your/service-account-key.json
      ```
        * Remember to grant the service account Editor access to your Google
          Sheet.

3. **[Optional] Set Quota Project:**  You might encounter a warning from the
   Sheets API indicating the need for a quota project. If you receive this
   warning, specify your project using:

    ```shell
    gcloud auth application-default set-quota-project name-of-your-project
    ```

   Replace `name-of-your-project` with your Google Cloud project ID. You can
   find this in the Google Cloud Console.

4. **Stream Output:**
   Now that authentication is configured, you can stream output as follows:

    ```shell
    UDMI_ROOT=path/to/your/udmi/project
    source $UDMI_ROOT/etc/shell_common.sh
    spreadsheet_id=YOUR_SPREADSHEET_ID  # Replace with your Google Sheet ID
    tool_name=YOUR_TOOL_NAME            # Replace with a name for the new sheet in your spreadsheet
    ```

    * **`spreadsheet_id`:**  This is the ID of your Google Sheet. It's typically
      found in the URL of your sheet (
      e.g., `https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit`).
    * **`tool_name`:**  This corresponds to the name of the tool for which you 
      are streaming the output. The utility appends the tool name with the 
      current timestamp to create a *new* sheet within your spreadsheet. 


Now you can use `stream_to_gsheets` with any command that produces output to `stdout`:

* **Example 1: Streaming output from `echo`:**
  ```shell
  echo "First line.
  Second line.
  Third line." | stream_to_gsheets "$tool_name" "$spreadsheet_id"
  ```

* **Example 2: Streaming the contents of a file:**

  ```shell
  cat path/to/some_file | stream_to_gsheets "$tool_name" "$spreadsheet_id"
  ```

   
In both examples, the output will be streamed to a new sheet in the specified 
Google Sheet. Each line of output will be written to a new row in the sheet.


### Using the utility with a tool

We have already integrated the `stream_to_gsheets` utility in `bin/registrar`. 
It can be used with other tools similarly.

* Define the link to the spreadsheet in your site model's site_metadata.json
  file under the key `sheet`
```json lines
// site_model/site_metadata.json
{
  "sheet": "https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID"
}
```
* In the tool script, add the required commands:
```shell
source $UDMI_ROOT/etc/shell_common.sh

# fetch the spreadsheet url from the site model
site_path=$(realpath "$1")
if [[ ! -d $site_path ]]; then
  site_path=$(dirname "$site_path")
fi

if [[ -e "$site_path/site_metadata.json" ]]; then
  spreadsheet=$(jq -r '.sheet' "$site_path/site_metadata.json")
else
  spreadsheet=
fi

if [[ $spreadsheet != "null" && -n "$spreadsheet" ]]; then
  spreadsheet_id=$(echo "$spreadsheet" | grep -oP '(?<=/d/)[^/]+')
  echo "Streaming logs to gsheet id $spreadsheet_id"
  
  # if spreadsheet url is available, use the utility to stream the output
  $cmd 2>&1 | tee $OUT_DIR/$util_name.log | stream_to_gsheets "$util_name" "$spreadsheet_id"
else
  $cmd 2>&1 | tee $OUT_DIR/$util_name.log
fi
```