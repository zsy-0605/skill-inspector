package io.github.skillinspector.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.skillinspector.model.InspectionReport;

public final class JsonReportRenderer {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public String render(InspectionReport report) {
        try { return mapper.writeValueAsString(report); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize report", e); }
    }
}
