package com.example.app;

import com.example.external.ExternalDependency;

public class FixtureApp {
    private final ExternalDependency dependency = new ExternalDependency();

    public String message() {
        return dependency.value();
    }
}
