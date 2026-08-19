[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Externals](#)

# Externals Model (Linking UDMI to Other Ontologies)

The `externals` block within a UDMI device's [`metadata.json`](metadata.md) file provides a standard mechanism to link the device to external ontologies or building information models. This allows UDMI to integrate with other industry-standard semantic models without needing to duplicate all their structural data.

Each entry within the `externals` block corresponds to a specific ontology or schema (e.g., `dbo`, `haystack`, `brick`, `bim`), and typically provides properties like `ext_id` (the external identifier) and `type` (the external class/type).

Below are conceptual examples of how different external models could be linked to a UDMI device using the `externals` block.

## Digital Buildings Ontology (DBO)

Google's [Digital Buildings Ontology](https://github.com/google/digitalbuildings) defines building assets.
In DBO, the `type` refers to a specific equipment class, and `ext_id` is a UUID identifying the entity.

```json
"externals": {
  "dbo": {
    "ext_id": "c773b86d-b0c0-46fc-bd3f-d726fadd5f1e",
    "type": "HVAC/VAV_SD_DSP"
  }
}
```

## Project Haystack

[Project Haystack](https://project-haystack.org/) is a semantic modeling ontology based on tags.
In Haystack, entities are typically identified by an opaque Ref (reference) string, and their type is implicitly defined by their marker tags (e.g., `vav`, `equip`).

```json
"externals": {
  "haystack": {
    "ext_id": "2180b666-7032054c",
    "type": "vav equip"
  }
}
```
*Note: The `ext_id` here represents the Haystack Ref (without the leading `@`). The `type` might contain a space-separated list of the primary marker tags identifying the equipment type.*

## Brick Schema

[Brick Schema](https://brickschema.org/) is an open-source ontology for building assets built on RDF/OWL.
In Brick, entities are nodes in a graph identified by URIs, and their types are Brick Classes.

```json
"externals": {
  "brick": {
    "ext_id": "bldg:VAV-1",
    "type": "brick:VAV"
  }
}
```
*Note: The `ext_id` is typically the entity URI or a namespaced local name (e.g. `bldg:VAV-1`). The `type` refers to a class in the Brick ontology.*

## BIM / IFC (Industry Foundation Classes)

[Building Information Modeling (BIM)](https://www.buildingsmart.org/) often uses the IFC open standard to exchange data.
In IFC, objects are uniquely identified by an IFC GlobalId (a 22-character Base64 encoded string) and categorized by IFC Classes.

```json
"externals": {
  "bim": {
    "ext_id": "1K1$W$h3v0$Q1mO6O$uR5R",
    "type": "IfcUnitaryEquipment"

  }
}
```
*Note: The `ext_id` is the 22-character IFC GlobalId. The `type` is the specific IFC class of the equipment.*
