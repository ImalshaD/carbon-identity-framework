/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.flow.mgt.model;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO class for reverting Flow in sub-organizations.
 */
public class FlowRevertDTO {

    private String flowType;
    private boolean revertFlow;
    private boolean revertFlowConfigs;
    private List<String> connectorPropertiesToRevert = new ArrayList<>();

    /**
     * Get the flow type.
     *
     * @return Flow type.
     */
    public String getFlowType() {

        return flowType;
    }

    /**
     * Set the flow type.
     *
     * @param flowType Flow type.
     */
    public void setFlowType(String flowType) {

        this.flowType = flowType;
    }

    /**
     * Check whether to revert the flow or not.
     *
     * @return True if the flow is to be reverted, false otherwise.
     */
    public boolean isRevertFlow() {

        return revertFlow;
    }

    /**
     * Set whether to revert the flow or not.
     *
     * @param revertFlow True if the flow is to be reverted, false otherwise.
     */
    public void setRevertFlow(boolean revertFlow) {

        this.revertFlow = revertFlow;
    }

    /**
     * Check whether to revert the flow configurations or not.
     *
     * @return True if the flow configurations are to be reverted, false otherwise.
     */
    public boolean isRevertFlowConfigs() {

        return revertFlowConfigs;
    }

    /**
     * Set whether to revert the flow configurations or not.
     *
     * @param revertFlowConfigs True if the flow configurations are to be reverted, false otherwise.
     */
    public void setRevertFlowConfigs(boolean revertFlowConfigs) {

        this.revertFlowConfigs = revertFlowConfigs;
    }

    /**
     * Get the list of properties associated with the flow revert operation.
     *
     * @return List of properties.
     */
    public List<String> getConnectorPropertiesToRevert() {

        return connectorPropertiesToRevert;
    }

    /**
     * Set the list of properties associated with the flow revert operation.
     *
     * @param connectorPropertiesToRevert List of properties.
     */
    public void setConnectorPropertiesToRevert(List<String> connectorPropertiesToRevert) {

        this.connectorPropertiesToRevert = connectorPropertiesToRevert;
    }
}

