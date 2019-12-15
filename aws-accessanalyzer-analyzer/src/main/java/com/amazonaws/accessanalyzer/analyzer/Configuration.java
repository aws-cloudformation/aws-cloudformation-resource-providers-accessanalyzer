package com.amazonaws.accessanalyzer.analyzer;

import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Configuration extends BaseConfiguration {

  Configuration() {
    super("aws-accessanalyzer-analyzer.json");
  }

  public JSONObject resourceSchemaJSONObject() {
    return new JSONObject(new JSONTokener(Objects
        .requireNonNull(this.getClass().getClassLoader().getResourceAsStream(schemaFilename))));
  }
}
