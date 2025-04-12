package com.example.webidle.model;

import java.util.List;

public class DebugRequest {
    private String code;
    private List<Integer> breakpoints;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<Integer> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(List<Integer> breakpoints) {
        this.breakpoints = breakpoints;
    }
} 