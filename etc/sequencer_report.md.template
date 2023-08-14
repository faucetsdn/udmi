# {{report.device_id}}

- Start {{report.start_time}}
- End: {{report.end_time}}
- Status: {{report.status_message}}

## Device Identification

| Device | AHU-1 |
|---|---|
| Site |  |
| Make | {{report.device_make}} |
| Model | {{report.device_model}} |
| Software | {{report.device_software|pretty_dict}} |

## Summary

| | Feature | {%- for stage in report.stages %} {{stage.capitalize()}} |{%- endfor %}
| --- | --- {{report.stages|length|md_table_header}}
{% for feature, stages in report.features.items() -%}
| {{report.overall_features.get(feature)|result_icon}} | {{feature}} | {%- for stage in report.stages %} {{stages[stage].scored}}/{{stages[stage].total}} |{%- endfor %}
{% endfor %}

## Results

| Bucket | Feature | Stage | Result | Description |
| --- | --- | --- | --- | --- |
{% for test in report.results.values() -%}
| {{test.bucket}} | {{test.name}} | {{test.stage}} | {{test.result}} | {{test.message}} |
{% endfor %}

## Schema

| Stage | Result | 
| --- | --- |
{% for schema, result in report.schema_results_by_stage.items() -%}
| {{schema}} | {{result|result_icon}} |
{% endfor %}
