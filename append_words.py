words = [
    "UCUM", "BACnetEngineeringUnits", "standardised", "QUDT", "Serialisation",
    "boolean", "serialised", "serialise", "KNX", "OPC", "enum", "unopinionated",
    "ETL", "cognisant", "contextualise", "LWT", "FCU"
]

with open(".wordlist.txt", "a") as f:
    for word in words:
        f.write(word + "\n")
