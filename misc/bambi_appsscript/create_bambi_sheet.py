"""This script creates a Google Sheet and a bound Apps Script project."""

import argparse
import os

import google
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError


SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/script.projects",
    "https://www.googleapis.com/auth/drive.metadata.readonly",
]

SCRIPT_FILES_DIR = os.path.join(os.path.dirname(__file__), "bambi_sheet")
DEFAULT_SHEET_TITLE = "BAMBI Sheet"
SCRIPT_PROJECT_TITLE = "BambiSheetsInterface"


def get_credentials():
  """Gets valid Application Default Credentials."""
  try:
    creds, _ = google.auth.default(scopes=SCOPES)
    if not creds or not creds.valid:
      if creds and creds.expired and creds.refresh_token:
        print("Refreshing credentials...")
        creds.refresh(Request())
      else:
        print(
            "Error: Application Default Credentials are not valid or could not"
            " be refreshed.\nEnsure 'gcloud auth application-default login' has"
            " been run with necessary scopes."
        )
        exit()
    return creds
  except google.auth.exceptions.DefaultCredentialsError as e:
    print(
        f"Error loading Application Default Credentials: {e}.\nPlease ensure"
        " you have authenticated via 'gcloud auth application-default login'"
        " with necessary scopes."
    )
    exit()
  except Exception as e:
    print(f"An unexpected error occurred during credential loading: {e}")
    exit()


def create_google_sheet(service, title):
  """Creates a new Google Sheet."""
  spreadsheet = {"properties": {"title": title}}
  try:
    sheet = (
        service.spreadsheets()
        .create(body=spreadsheet, fields="spreadsheetId,spreadsheetUrl")
        .execute()
    )
    print(
        f"Created Google Sheet:\n Title: {title}\n ID:"
        f" {sheet.get('spreadsheetId')}\n URL: {sheet.get('spreadsheetUrl')}\n"
    )
    return sheet
  except HttpError as error:
    print(f"An error occurred creating the sheet: {error}")
    return None


def create_bound_script_project(service, title, parent_id):
  """Creates a new Apps Script project bound to a parent (e.g., a Sheet)."""
  request_body = {
      "title": title,
      "parentId": parent_id,  # ID of the Google Sheet
  }
  try:
    project = service.projects().create(body=request_body).execute()
    script_id = project.get("scriptId")
    editor_url = f"https://script.google.com/d/{script_id}/edit"
    print(
        f"Created Apps Script project:\n Title: {title}\n Script ID:"
        f" {script_id}\n Editor URL: {editor_url}\n"
    )
    return project
  except HttpError as error:
    print(f"An error occurred creating the script project: {error}")
    return None


def get_script_content(script_dir):
  """Reads .gs and appsscript.json files from the specified directory."""
  files_to_upload = []
  manifest_exists = False

  if not os.path.isdir(script_dir):
    print(
        f"Error: Script directory '{script_dir}' not found or is not a"
        " directory."
    )
    return None

  for filename in os.listdir(script_dir):
    filepath = os.path.join(script_dir, filename)
    if os.path.isfile(filepath):
      file_type = None
      if filename.lower() == "appsscript.json":
        file_type = "JSON"
        manifest_exists = True
      elif filename.lower().endswith(".gs"):
        file_type = "SERVER_JS"
      elif filename.lower().endswith(".html"):
        file_type = "HTML"

      if file_type:
        try:
          with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
          files_to_upload.append({
              "name": os.path.splitext(filename)[0],
              "type": file_type,
              "source": content,
          })
        except Exception as e:
          print(f"Error reading file {filepath}: {e}")

  if not manifest_exists:
    print(f"Warning: appsscript.json not found in {script_dir}. ")

  return files_to_upload


def update_script_project_content(service, script_id, files_payload):
  """Updates the content of an Apps Script project."""
  if not files_payload:
    print("No script files found or prepared to upload.")
    return False

  request_body = {"files": files_payload}
  try:
    service.projects().updateContent(
        scriptId=script_id, body=request_body
    ).execute()
    print(f"Successfully updated content for script ID: {script_id}")
    return True
  except HttpError as error:
    print(f"An error occurred updating script content: {error}")
    if hasattr(error, "content"):
      print(f"Error details: {error.content}")
    return False


def main():
  parser = argparse.ArgumentParser(
      description="Create a Google Sheet and a bound Apps Script project."
  )
  parser.add_argument(
      "--sheet_title",
      type=str,
      default=DEFAULT_SHEET_TITLE,
      help=(
          "The title for the new Google Sheet. Defaults to"
          f" '{DEFAULT_SHEET_TITLE}'."
      ),
  )
  args = parser.parse_args()

  new_sheet_title = args.sheet_title

  creds = get_credentials()
  if not creds:
    print("Failed to get credentials. Exiting.")
    return

  sheets_service = build("sheets", "v4", credentials=creds)
  script_service = build("script", "v1", credentials=creds)

  print(f"\nAttempting to create Google Sheet with title: '{new_sheet_title}'")
  spreadsheet = create_google_sheet(sheets_service, new_sheet_title)
  if not spreadsheet:
    return
  spreadsheet_id = spreadsheet.get("spreadsheetId")

  print(
      "\nAttempting to create Apps Script project titled:"
      f" '{SCRIPT_PROJECT_TITLE}' bound to sheet ID: {spreadsheet_id}"
  )
  script_project = create_bound_script_project(
      script_service, SCRIPT_PROJECT_TITLE, spreadsheet_id
  )
  if not script_project:
    return
  script_id = script_project.get("scriptId")

  print(f"\nReading script files from: '{SCRIPT_FILES_DIR}'")
  files_to_upload = get_script_content(SCRIPT_FILES_DIR)
  if files_to_upload is None:
    print("Could not read script files. Aborting script update.")
    return
  if not files_to_upload:
    print(
        "Warning: No valid script files (appsscript.json, .gs, .html) found "
        f"in '{SCRIPT_FILES_DIR}'."
    )
  else:
    print(
        f"\nAttempting to update script project ID: {script_id} with content."
    )
    update_script_project_content(script_service, script_id, files_to_upload)

  print("\n--- Process Complete ---")
  print(f"Sheet URL: {spreadsheet.get('spreadsheetUrl')}")
  print(f"Apps Script Editor URL: https://script.google.com/d/{script_id}/edit")
  print(
      "You can also access the script via the Sheet: Extensions > Apps Script"
  )


if __name__ == "__main__":
  main()
