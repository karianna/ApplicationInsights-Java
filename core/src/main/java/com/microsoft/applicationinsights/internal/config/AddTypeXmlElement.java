/*
 * AppInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.config;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by gupele on 3/15/2015.
 */
@XmlRootElement(name="Add")
public class AddTypeXmlElement {
    private String type;
    private ArrayList<ParamXmlElement> paramElements = new ArrayList<ParamXmlElement>();

    public String getType() {
        return type;
    }

    public ArrayList<ParamXmlElement> getParameters() {
        return paramElements;
    }

    @XmlAttribute
    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name="Param")
    public void setParameters(ArrayList<ParamXmlElement> paramElements) {
        this.paramElements = paramElements;
    }

    public Map<String, String> getData() {
        Map<String, String> map = new HashMap<String, String>();

        for (ParamXmlElement element : getParameters()) {
            map.put(element.getName(), element.getValue());
        }

        return map;
    }
}
