package com.amazonaws.accessanalyzer.analyzer;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-accessanalyzer-analyzer.json");
    }
}
