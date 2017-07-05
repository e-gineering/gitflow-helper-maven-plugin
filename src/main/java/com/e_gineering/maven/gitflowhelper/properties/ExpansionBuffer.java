package com.e_gineering.maven.gitflowhelper.properties;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Shamelessly cribbed from https://raw.githubusercontent.com/mojohaus/properties-maven-plugin/master/src/main/java/org/codehaus/mojo/properties/ExpansionBuffer.java
 */
public class ExpansionBuffer {
    private final StringBuilder resolved = new StringBuilder();

    private String unresolved;

    public ExpansionBuffer(String unresolved) {
        this.unresolved = unresolved != null ? unresolved : "";
    }

    public boolean hasMoreLegalPlaceholders() {
        int prefixPos = unresolved.indexOf("${");
        int suffixPos = unresolved.indexOf("}", prefixPos + 2);
        return prefixPos >= 0 && suffixPos >= 0;
    }

    public String extractPropertyKey() {
        advanceToNextPrefix();

        discardPrefix();

        String key = beforeNextSuffix();

        discardToAfterNextSuffix();

        return key;
    }

    public String toString() {
        // Note: Mutation should not occur in toString else logging, debugging etc will cause erroneous behaviour
        return resolved.toString() + unresolved;
    }

    public void add(String newKey, String newValue) {
        if (replaced(newValue)) {
            expandFurther(newValue);
        } else {
            skipUnresolvedPlaceholder(newKey);
        }
    }

    private boolean replaced(String value) {
        return value != null;
    }

    private void expandFurther(String value) {
        unresolved = value + unresolved;
    }

    private void skipUnresolvedPlaceholder(String newKey) {
        resolved.append("${").append(newKey).append("}");
    }

    private void discardToAfterNextSuffix() {
        int propertySuffixPos = unresolved.indexOf("}");
        unresolved = unresolved.substring(propertySuffixPos + 1);
    }

    private void advanceToNextPrefix() {
        resolved.append(beforePrefix());
    }

    private void discardPrefix() {
        int propertyPrefixPos = unresolved.indexOf("${");
        unresolved = unresolved.substring(propertyPrefixPos + 2);
    }

    private String beforePrefix() {
        int propertyPrefixPos = unresolved.indexOf("${");
        return unresolved.substring(0, propertyPrefixPos);
    }

    private String beforeNextSuffix() {
        int propertySuffixPos = unresolved.indexOf("}");
        return unresolved.substring(0, propertySuffixPos);
    }
}
