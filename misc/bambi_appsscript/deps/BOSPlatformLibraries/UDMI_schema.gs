/* BOS Platform Libraries
   UDMI Schema functions
   2025 fanselmo@google.com
*/


function generateJSONFromSchema(githubRepoOwner, githubRepoName, schema, level) {
  const result = {};
  Logger.log("Schema: " + JSON.stringify(schema));
  try {
    if ("properties" in schema) {
      for (const key in schema.properties) {
        Logger.log("Key: " + key);
        const property = schema.properties[key];
        Logger.log("Type: " + property.type);
        if (property.type === "object") {
          // catch maps and hash maps
          if ("existingJavaType" in property) {
            if (property.existingJavaType == "java.util.Map<String, String>") {
              // software
              let example = "";
              if ("examples" in property) {
                  example = property.examples;
                  Logger.log("Example: " + example);
              }
              result[key] = getExampleValue(property.type, example);
            }
          } else {
            result[key] = generateJSONFromSchema(githubRepoOwner, githubRepoName, property, level);
          }
        } else if (property.type === "array") {
          if (property.items.pattern != "") {
            result[key] = "example_string";
          } else {
            result[key] = [generateJSONFromSchema(githubRepoOwner, githubRepoName, property.items, level)];
          }
        } else if ("existingJavaType" in property) {
              if (property.existingJavaType == "java.util.HashMap<String, FamilyLocalnetModel>") {
                // families
                let example = "";
                Logger.log("FamilyLocalnetModel");
                Logger.log(property);
                Logger.log(property.patternProperties);
                Logger.log(getFirstDictionaryItem(property.patternProperties));
                // get $ref from patternProperties
                var regexItem = getFirstDictionaryItem(property.patternProperties);

                    if ( regexItem.$ref != "") {
                      Logger.log("$ref: "+regexItem.$ref);
                      let filename = []
                      if ( regexItem.$ref.startsWith("file:")) {
                        filename = regexItem.$ref.split(":");
                        console.log("Filename:", filename[1]);

                        const childSchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, "master/schema/"+filename[1]);
                        Logger.log("Child schema filename:", filename[1]);
                        Logger.log("Child schema:", childSchema);
                        var childItems = {};
                        if (level > 1) {
                          // result[key] = generateJSONFromSchema(githubRepoOwner, githubRepoName, childSchema, level - -1);
                          childItems = generateJSONFromSchema(githubRepoOwner, githubRepoName, childSchema, level -1);
                          Logger.log(childItems);
                        }
                      }
                    }


                if ("examples" in property) {
                    example = property.examples;
                    Logger.log("Example: " + example);
                }
                result[key] = getExampleValue(property.type, example, childItems);
              }
        }
        else if ("enum" in property) {
          let example = property.enum[0];
          result[key] = getExampleValue(property.type, example);
        } else {
          // parse the reference file if a $ref object is present
          if ("$ref" in property) {
            if (property.$ref != "") {
              Logger.log(property.$ref);
              let filename = []
              if (property.$ref.startsWith("file:")) {
                filename = property.$ref.split(":");
                console.log("Filename:", filename[1]);

                const childSchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, "master/schema/"+filename[1]);
                Logger.log("Child schema filename:", filename[1]);
                Logger.log("Child schema:", childSchema);
                if (level > 1) {
                  // result[key] = generateJSONFromSchema(githubRepoOwner, githubRepoName, childSchema, level - -1);
                  result[key] = generateJSONFromSchema(githubRepoOwner, githubRepoName, childSchema, level -1);
                }
              }
            }
          } else {
            let example = "";
            if ("examples" in property) {
                example = property.examples[0];
                Logger.log("Example: " + example);
            }
            result[key] = getExampleValue(property.type, example);
          }
        }
      }
    } else {
      // result[key] = ["example_string"];
      Logger.log ("No properties in schema: " + schema);
    }

  } catch (error) {
      Logger.log("Error: " + error);
  }

  Logger.log("Result: " + JSON.stringify(result));
  return result;
}

function parseInternalRef(jsonData) {
  const refPath = jsonData.someProperty.$ref; // Assuming $ref is under 'someProperty'

  // Split the reference path into segments
  const segments = refPath.split('/').slice(1); // Remove the initial '#'

  let referencedSchema = jsonData;
  for (const segment of segments) {
    referencedSchema = referencedSchema[segment];
  }

  // Now you have the referenced schema in 'referencedSchema'
  // Merge or replace as needed
}

// function parseExternalRef(jsonData) {
//   const refPath = jsonData.someProperty.$ref;
//   const externalSchemaUrl = "https://example.com/schemas/external-schema.json";

//   const response = UrlFetchApp.fetch(externalSchemaUrl);
//   const externalSchema = JSON.parse(response.getContentText());

//   // ... (Similar logic as in the internal reference example to navigate to the referenced location)
// }


function getExampleValue(type, example, childSchema) {
  if (example != "") {
    if (isArray(example)) {
      return convertArrayToDictionaryWithChildren(example, childSchema);
    } else {
      return example;
    }
  }
  else {
    switch (type) {
      case "enum":
        return "ENUM";
      case "string":
        return "example_string";
      case "number":
        return 123;
      case "integer":
        return 456;
      case "boolean":
        return true;
      default:
        return null;
    }
  }
}

function prettyPrintJSON(jsonString) {
  try {
    const jsonObject = JSON.parse(jsonString);
    const prettyJsonString = JSON.stringify(jsonObject, null, 4); // 4 spaces for indentation
    return prettyJsonString;
  } catch (error) {
    // Handle invalid JSON gracefully
    console.error("Error parsing JSON:", error);
    return "Invalid JSON input"; // Or handle the error differently as needed
  }
}

function getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, jsonFilePath) {

  const url = `https://raw.githubusercontent.com/${githubRepoOwner}/${githubRepoName}/${jsonFilePath}`;

  const response = UrlFetchApp.fetch(url);
  const content = response.getContentText();
  const jsonData = JSON.parse(content);

  // console.log(jsonData);

  return jsonData
}

function testGetUDMISchema() {

  const githubRepoOwner = 'faucetsdn';
  const githubRepoName = 'udmi';
  // const jsonFilePath = 'master/schema/model_pointset.json';
  // const jsonFilePath = 'master/schema/model_system.json';
  const jsonFilePath = 'master/schema/model_localnet.json';

  let mySchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, jsonFilePath);

  let jsonData = generateJSONFromSchema(githubRepoOwner, githubRepoName, mySchema, 5);

  // New object to add
  const deviceId = { "device_id": "TEST-1" };

  // Create a new object with the new object at the beginning
  const result = { ...deviceId, ...jsonData };

  console.log(result);

}


// "type": "object",
// "existingJavaType": "java.util.Map<String, String>",


