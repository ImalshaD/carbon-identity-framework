/*
 * Copyright (c) 2021-2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.idp.mgt.dao;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang.StringUtils;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.carbon.identity.action.management.api.model.EndpointConfig;
import org.wso2.carbon.identity.application.common.model.Claim;
import org.wso2.carbon.identity.application.common.model.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdPGroup;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.IdentityProviderProperty;
import org.wso2.carbon.identity.application.common.model.JustInTimeProvisioningConfig;
import org.wso2.carbon.identity.application.common.model.LocalRole;
import org.wso2.carbon.identity.application.common.model.PermissionsAndRoleConfig;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.ProvisioningConnectorConfig;
import org.wso2.carbon.identity.application.common.model.RoleMapping;
import org.wso2.carbon.identity.application.common.model.UserDefinedFederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.base.AuthenticatorPropertyConstants.DefinedByType;
import org.wso2.carbon.identity.core.model.ExpressionNode;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
import org.wso2.carbon.identity.secret.mgt.core.SecretManagerImpl;
import org.wso2.carbon.identity.secret.mgt.core.model.SecretType;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementClientException;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementException;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementServerException;
import org.wso2.carbon.idp.mgt.internal.IdpMgtServiceComponentHolder;
import org.wso2.carbon.idp.mgt.model.ConnectedAppsResult;
import org.wso2.carbon.idp.mgt.util.ActionMgtTestUtil;
import org.wso2.carbon.idp.mgt.util.IdPManagementConstants;
import org.wso2.carbon.idp.mgt.util.IdPSecretsProcessor;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.wso2.carbon.idp.mgt.util.IdPManagementConstants.RESET_PROVISIONING_ENTITIES_ON_CONFIG_UPDATE;

/**
 * Unit tests for IdPManagementDAO.
 */
public class IdPManagementDAOTest {

    private static final String DB_NAME = "test";
    private static final Integer SAMPLE_TENANT_ID = -1234;
    private static final Integer SAMPLE_TENANT_ID2 = 1;
    private static final Integer SAMPLE_TENANT_ID3 = 2;

    private static final String TENANT_DOMAIN = "carbon.super";
    private static final String IDP_GROUP1 = "idpGroup1";
    private static final String IDP_GROUP2 = "idpGroup2";
    private static final String IDP_GROUP2_ID = "idpGroup2Id";
    private static Map<String, BasicDataSource> dataSourceMap = new HashMap<>();

    private static final String ASSOCIATED_ACTION_ID = "Dummy_Action_ID";
    private static final String CUSTOM_IDP_NAME = "customIdP";
    private static EndpointConfig endpointConfig;
    private static EndpointConfig endpointConfigToBeUpdated;
    private IdentityProvider idpForErrorScenarios;
    private IdentityProvider userDefinedIdP;

    MockedStatic<IdentityTenantUtil> identityTenantUtil;
    MockedStatic<CryptoUtil> cryptoUtil;
    MockedStatic<OrganizationManagementUtil> organizationManagementUtilMockedStatic;
    private SecretManagerImpl secretManager;
    private CryptoUtil mockCryptoUtil;

    private IdPManagementDAO idPManagementDAO;

    public static void closeH2Database() throws Exception {

        BasicDataSource dataSource = dataSourceMap.get(DB_NAME);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static String getFilePath(String fileName) {

        if (StringUtils.isNotBlank(fileName)) {
            return Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "dbscripts", fileName)
                    .toString();
        }
        throw new IllegalArgumentException("DB Script file name cannot be empty.");
    }

    private static Connection getConnection(String database) throws SQLException {

        if (dataSourceMap.get(database) != null) {
            return dataSourceMap.get(database).getConnection();
        }
        throw new RuntimeException("No datasource initiated for database: " + database);
    }

    private void initiateH2Database(String databaseName, String scriptPath) throws Exception {

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUsername("username");
        dataSource.setPassword("password");
        dataSource.setUrl("jdbc:h2:mem:test" + databaseName);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("RUNSCRIPT FROM '" + scriptPath + "'");
        }
        dataSourceMap.put(databaseName, dataSource);
    }

    @BeforeClass
    public void setUp() throws Exception {

        secretManager = mock(SecretManagerImpl.class);
        SecretType secretType = mock(SecretType.class);
        IdpMgtServiceComponentHolder.getInstance().setSecretManager(secretManager);
        when(secretType.getId()).thenReturn("secretId");
        doReturn(secretType).when(secretManager).getSecretType(any());
        when(secretManager.isSecretExist(anyString(), anyString())).thenReturn(false);

        cryptoUtil = mockStatic(CryptoUtil.class);
        mockCryptoUtil = mock(CryptoUtil.class);
        organizationManagementUtilMockedStatic = mockStatic(OrganizationManagementUtil.class);
        cryptoUtil.when(CryptoUtil::getDefaultCryptoUtil).thenReturn(mockCryptoUtil);
        when(mockCryptoUtil.encryptAndBase64Encode(any())).thenReturn("ENCRYPTED_VALUE2");
        when(mockCryptoUtil.base64DecodeAndDecrypt(anyString())).thenReturn("ENCRYPTED_VALUE2".getBytes());
        organizationManagementUtilMockedStatic.when(() -> OrganizationManagementUtil.isOrganization(TENANT_DOMAIN))
                .thenReturn(false);
        endpointConfig = ActionMgtTestUtil.createEndpointConfig("http://localhost", "admin", "admin");
        endpointConfigToBeUpdated = ActionMgtTestUtil.createEndpointConfig("http://localhost1", "admin1", "admin1");
        userDefinedIdP = ActionMgtTestUtil.createIdPWithUserDefinedFederatedAuthenticatorConfig(CUSTOM_IDP_NAME, endpointConfig);
        idpForErrorScenarios = ActionMgtTestUtil.createIdPWithUserDefinedFederatedAuthenticatorConfig(
                CUSTOM_IDP_NAME + "Error", endpointConfig);
    }

    @AfterClass
    public void tearDownClass() {

        cryptoUtil.close();
        organizationManagementUtilMockedStatic.close();
    }

    @BeforeMethod
    public void setup() throws Exception {

        IdPSecretsProcessor idpSecretsProcessor = mock(IdPSecretsProcessor.class);
        when(idpSecretsProcessor.decryptAssociatedSecrets(any())).thenAnswer(
                invocation -> invocation.getArguments()[0]);
        when(idpSecretsProcessor.encryptAssociatedSecrets(any())).thenAnswer(
                invocation -> invocation.getArguments()[0]);
        idPManagementDAO = new IdPManagementDAO();
        Field idpSecretsProcessorField = IdPManagementDAO.class.getDeclaredField("idpSecretsProcessorService");
        idpSecretsProcessorField.setAccessible(true);
        idpSecretsProcessorField.set(idPManagementDAO, idpSecretsProcessor);

        initiateH2Database(DB_NAME, getFilePath("h2.sql"));
        identityTenantUtil = mockStatic(IdentityTenantUtil.class);
        identityTenantUtil.when(() -> IdentityTenantUtil.getTenantDomain(anyInt())).thenReturn(TENANT_DOMAIN);
    }

    @AfterMethod
    public void tearDown() throws Exception {

        closeH2Database();
        identityTenantUtil.close();
    }

    @DataProvider
    public Object[][] getIdPsData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, 2},
                {SAMPLE_TENANT_ID2, TENANT_DOMAIN, 2},
                {4, TENANT_DOMAIN, 0},
        };
    }

    @Test(dataProvider = "getIdPsData")
    public void testGetIdPs(int tenantId, String tenantDomain, int resultCount) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            List<IdentityProvider> idps1 = idPManagementDAO.getIdPs(connection, tenantId, tenantDomain);
            assertEquals(idps1.size(), resultCount);
            for (IdentityProvider idp : idps1) {
                validateAdminForcedPasswordResetEmailLinkProp(idp);
            }
            List<IdentityProvider> idps2 = idPManagementDAO.getIdPs(null, tenantId, tenantDomain);
            assertEquals(idps2.size(), resultCount);
            for (IdentityProvider idp : idps2) {
                validateAdminForcedPasswordResetEmailLinkProp(idp);
            }
        }
    }

    private void validateAdminForcedPasswordResetEmailLinkProp(IdentityProvider idp) {

        IdentityProviderProperty[] identityProviderProperties = idp.getIdpProperties();
        for (IdentityProviderProperty identityProviderProperty : identityProviderProperties) {
            if (identityProviderProperty.getName()
                    .equals(IdPManagementConstants.ENABLE_ADMIN_PASSWORD_RESET_EMAIL_LINK_PROPERTY)) {
                assertEquals(identityProviderProperty.getValue(), "true");
            }
        }
    }

    @Test(dataProvider = "getIdPsData")
    public void testGetIdPsException(int tenantId, String tenantDomain, int resultCount) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.getIdPs(null, tenantId, tenantDomain));
        }
    }

    @DataProvider
    public Object[][] getIdPsSearchData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "testIdP1", 1},
                {SAMPLE_TENANT_ID2, TENANT_DOMAIN, "testIdP3", 1},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "", 2},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "test*", 2},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "????IdP*", 2},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "tes_I*", 2},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "*1", 1},
                {SAMPLE_TENANT_ID, TENANT_DOMAIN, "Notexist", 0},
        };
    }

    @Test(dataProvider = "getIdPsSearchData")
    public void testGetIdPsSearch(int tenantId, String tenantDomain, String filter, int resultCount) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            List<IdentityProvider> idps1 = idPManagementDAO.getIdPsSearch(connection, tenantId, tenantDomain, filter);
            assertEquals(idps1.size(), resultCount);
            List<IdentityProvider> idps2 = idPManagementDAO.getIdPsSearch(null, tenantId, tenantDomain, filter);
            assertEquals(idps2.size(), resultCount);
        }
    }

    @Test(dataProvider = "getIdPsSearchData")
    public void testGetIdPsSearchException(int tenantId, String tenantDomain, String filter, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.getIdPsSearch(null, tenantId, tenantDomain, filter));
        }
    }

    @DataProvider
    public Object[][] getTrustedTokenIssuersData() {

        ExpressionNode expressionNode = new ExpressionNode();
        List<ExpressionNode> expressionNodesList = new ArrayList<>();
        expressionNodesList.add(expressionNode);

        List<String> attributes1 = Arrays.asList("id", "name", "description", "isEnabled", "image", "isPrimary");
        List<String> attributes2 = Arrays.asList("homeRealmIdentifier", "isFederationHub", "certificate", "alias",
                "claims", "roles", "federatedAuthenticators", "provisioning");

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList, 2, 0, "ASC", "NAME", attributes1, 1},
                {SAMPLE_TENANT_ID, expressionNodesList, 1, 1, "ASC", "NAME", attributes2, 0},
                {SAMPLE_TENANT_ID, expressionNodesList, 2, 0, "DESC", "NAME", attributes1, 1},
        };
    }

    @Test(dataProvider = "getTrustedTokenIssuersData")
    public void testGetTrustedTokenIssuers(int tenantId, List<ExpressionNode> expressionNodes, int limit, int offset,
                                           String sortOrder, String sortBy, List<String> attributes, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestTrustedTokenIssuers();

            List<IdentityProvider> idps1 = idPManagementDAO.getTrustedTokenIssuerSearch(tenantId,
                    expressionNodes, limit, offset, sortOrder, sortBy, attributes);
            assertEquals(idps1.size(), resultCount);
        }
    }

    @Test(dataProvider = "getIdPsSearchData")
    public void testGetTrustedTokenIssuersException(int tenantId, String tenantDomain, String filter, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.getIdPsSearch(null, tenantId, tenantDomain, filter));
        }
    }

    @DataProvider
    public Object[][] getIdPsSearchWithExpressionNodesData() {

        ExpressionNode expressionNode1 = new ExpressionNode();
        expressionNode1.setAttributeValue("name");
        expressionNode1.setOperation("co");
        expressionNode1.setValue("IdP");
        List<ExpressionNode> expressionNodesList1 = new ArrayList<>();
        expressionNodesList1.add(expressionNode1);

        ExpressionNode expressionNode2 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList2 = new ArrayList<>();
        expressionNode2.setAttributeValue("name");
        expressionNode2.setOperation("eq");
        expressionNode2.setValue("testIdP1");
        expressionNodesList2.add(expressionNode2);

        ExpressionNode expressionNode3 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList3 = new ArrayList<>();
        expressionNode3.setAttributeValue("name");
        expressionNode3.setOperation("ew");
        expressionNode3.setValue("2");
        expressionNodesList3.add(expressionNode3);

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList1, 2, 0, "ASC", "NAME", 2, "testIdP1"},
                {SAMPLE_TENANT_ID, expressionNodesList1, 2, 0, "DESC", "NAME", 2, "testIdP2"},
                {SAMPLE_TENANT_ID2, expressionNodesList1, 1, 1, "ASC", "NAME", 1, "testIdP3"},
                {SAMPLE_TENANT_ID, expressionNodesList2, 1, 0, "ASC", "NAME", 1, "testIdP1"},
                {SAMPLE_TENANT_ID, expressionNodesList3, 1, 0, "ASC", "NAME", 1, "testIdP2"},
        };
    }

    @Test(dataProvider = "getIdPsSearchWithExpressionNodesData")
    public void testGetIdPsSearchWithExpressionNodes(int tenantId, List<ExpressionNode> expressionNodes, int limit,
                                                     int offset, String sortOrder, String sortBy, int count,
                                                     String firstIdp) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            List<IdentityProvider> idps = idPManagementDAO.getIdPsSearch(tenantId, expressionNodes, limit, offset,
                    sortOrder, sortBy);
            assertEquals(idps.size(), count);
            if (count > 0) {
                assertEquals(idps.get(0).getIdentityProviderName(), firstIdp);
            }
        }
    }

    @DataProvider
    public Object[][] getIdPsSearchWithExpressionNodesExceptionData() {

        ExpressionNode expressionNode1 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList1 = new ArrayList<>();
        expressionNodesList1.add(expressionNode1);

        ExpressionNode expressionNode2 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList2 = new ArrayList<>();
        expressionNode2.setAttributeValue("InvalidAttribute");
        expressionNode2.setOperation("eq");
        expressionNode2.setValue("testIdP1");
        expressionNodesList2.add(expressionNode2);

        ExpressionNode expressionNode3 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList3 = new ArrayList<>();
        expressionNode3.setAttributeValue("description");
        expressionNode3.setOperation("InvalidOperation");
        expressionNode3.setValue("2");
        expressionNodesList3.add(expressionNode3);

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList1, 2, 0, "WrongOrder", "NAME", "ServerException"},
                {SAMPLE_TENANT_ID, expressionNodesList1, 2, 0, "DESC", "WrongBy", "ServerException"},
                {SAMPLE_TENANT_ID, expressionNodesList2, 1, 0, "ASC", "NAME", "ClientException"},
                {SAMPLE_TENANT_ID, expressionNodesList3, 1, 0, "ASC", "NAME", "ClientException"},
        };
    }

    @Test(dataProvider = "getIdPsSearchWithExpressionNodesExceptionData")
    public void testGetIdPsSearchWithExpressionNodesException(int tenantId, List<ExpressionNode> expressionNodes,
                                                              int limit, int offset, String sortOrder, String sortBy,
                                                              String exceptionType) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            if (exceptionType.equals("ServerException")) {
                assertThrows(IdentityProviderManagementServerException.class, () -> idPManagementDAO.getIdPsSearch
                        (tenantId, expressionNodes, limit, offset, sortOrder, sortBy));
            } else if (exceptionType.equals("ClientException")) {
                assertThrows(IdentityProviderManagementClientException.class, () -> idPManagementDAO.getIdPsSearch
                        (tenantId, expressionNodes, limit, offset, sortOrder, sortBy));
            }
        }
    }

    @DataProvider
    public Object[][] getIdPsSearchWithAttributesData() {

        ExpressionNode expressionNode = new ExpressionNode();
        List<ExpressionNode> expressionNodesList = new ArrayList<>();
        expressionNodesList.add(expressionNode);

        List<String> attributes1 = Arrays.asList("id", "name", "description", "isEnabled", "image", "isPrimary");
        List<String> attributes2 = Arrays.asList("homeRealmIdentifier", "isFederationHub", "certificate", "alias",
                "claims", "roles", "federatedAuthenticators", "provisioning");

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList, 2, 0, "ASC", "NAME", attributes1, 2},
                {SAMPLE_TENANT_ID, expressionNodesList, 1, 1, "ASC", "NAME", attributes2, 1},
                {SAMPLE_TENANT_ID, expressionNodesList, 2, 0, "DESC", "NAME", attributes1, 2},
        };
    }

    @Test(dataProvider = "getIdPsSearchWithAttributesData")
    public void testGetIdPsSearchWithAttributes(int tenantId, List<ExpressionNode> expressionNodes, int limit,
                                                int offset, String order, String sortBy, List<String> attributes,
                                                int count) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            List<IdentityProvider> idps = idPManagementDAO.getIdPsSearch(tenantId, expressionNodes, limit, offset,
                    order, sortBy, attributes);
            assertEquals(idps.size(), count);
        }
    }

    @DataProvider
    public Object[][] getIdPsSearchWithAttributesExceptionData() {

        ExpressionNode en = new ExpressionNode();
        List<ExpressionNode> expressionNodes = new ArrayList<>();
        expressionNodes.add(en);
        List<String> attributes = Arrays.asList("WrongAttribute");

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodes, 2, 0, "ASC", "NAME", attributes},
        };
    }

    @Test(dataProvider = "getIdPsSearchWithAttributesExceptionData")
    public void testGetIdPsSearchWithAttributesException(int tenantId, List<ExpressionNode> expressionNodes, int limit,
                                                         int offset, String order, String sortBy,
                                                         List<String> attributes) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementClientException.class, () -> idPManagementDAO.getIdPsSearch
                    (tenantId, expressionNodes, limit, offset, order, sortBy, attributes));
        }
    }

    @DataProvider
    public Object[][] getCountOfFilteredIdPsData() {

        ExpressionNode expressionNode1 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList1 = new ArrayList<>();
        expressionNodesList1.add(expressionNode1);
        ExpressionNode expressionNode2 = new ExpressionNode();
        expressionNode2.setAttributeValue("name");
        expressionNode2.setOperation("sw");
        expressionNode2.setValue("test");
        List<ExpressionNode> expressionNodesList2 = new ArrayList<>();
        expressionNodesList2.add(expressionNode2);

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList1, 2},
                {SAMPLE_TENANT_ID, expressionNodesList2, 2},
                {SAMPLE_TENANT_ID2, expressionNodesList1, 2},
        };
    }

    @Test(dataProvider = "getCountOfFilteredIdPsData")
    public void testGetCountOfFilteredIdPs(int tenantId, List<ExpressionNode> expNodes, int count) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            int resultCount = idPManagementDAO.getCountOfFilteredIdPs(tenantId, expNodes);
            assertEquals(resultCount, count);
        }
    }

    @DataProvider
    public Object[][] getCountOfFilteredTrustedTokenIssuersData() {

        ExpressionNode expressionNode1 = new ExpressionNode();
        List<ExpressionNode> expressionNodesList1 = new ArrayList<>();
        expressionNodesList1.add(expressionNode1);
        ExpressionNode expressionNode2 = new ExpressionNode();
        expressionNode2.setAttributeValue("name");
        expressionNode2.setOperation("sw");
        expressionNode2.setValue("test");
        List<ExpressionNode> expressionNodesList2 = new ArrayList<>();
        expressionNodesList2.add(expressionNode2);

        return new Object[][]{
                {SAMPLE_TENANT_ID, expressionNodesList1, 1},
                {SAMPLE_TENANT_ID, expressionNodesList2, 1},
                {SAMPLE_TENANT_ID2, expressionNodesList1, 0},
        };
    }

    @Test(dataProvider = "getCountOfFilteredTrustedTokenIssuersData")
    public void testGetCountOfFilteredTrustedTokenIssuers(int tenantId, List<ExpressionNode> expNodes, int count)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestTrustedTokenIssuers();

            int resultCount = idPManagementDAO.getCountOfFilteredTokenIssuers(tenantId, expNodes);
            assertEquals(resultCount, count);
        }
    }

    @Test(dataProvider = "getCountOfFilteredIdPsData")
    public void testGetCountOfFilteredIdPsException(int tenantId, List<ExpressionNode> expNodes, int count)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.getCountOfFilteredIdPs(tenantId, expNodes));
        }
    }

    @DataProvider
    public Object[][] addIdPData() {

        // Initialize Test Identity Provider 1.
        IdentityProvider idp1 = new IdentityProvider();
        idp1.setIdentityProviderName("testIdP1");
        idp1.setEnable(true);
        idp1.setPrimary(true);
        idp1.setFederationHub(true);
        idp1.setCertificate("");

        RoleMapping roleMapping1 = new RoleMapping(new LocalRole("1", "LocalRole1"), "Role1");
        RoleMapping roleMapping2 = new RoleMapping(new LocalRole("2", "LocalRole2"), "Role2");
        PermissionsAndRoleConfig permissionsAndRoleConfig = new PermissionsAndRoleConfig();
        permissionsAndRoleConfig.setIdpRoles(new String[]{"Role1", "Role2"});
        permissionsAndRoleConfig.setRoleMappings(new RoleMapping[]{roleMapping1, roleMapping2});
        idp1.setPermissionAndRoleConfig(permissionsAndRoleConfig);

        FederatedAuthenticatorConfig federatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
        federatedAuthenticatorConfig.setDisplayName("DisplayName1");
        federatedAuthenticatorConfig.setName("Name");
        federatedAuthenticatorConfig.setEnabled(true);
        federatedAuthenticatorConfig.setDefinedByType(DefinedByType.SYSTEM);
        Property property1 = new Property();
        property1.setName("Property1");
        property1.setValue("value1");
        property1.setConfidential(false);
        Property property2 = new Property();
        property2.setName("Property2");
        property2.setValue("value2");
        property2.setConfidential(true);
        federatedAuthenticatorConfig.setProperties(new Property[]{property1, property2});
        idp1.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{federatedAuthenticatorConfig});

        ProvisioningConnectorConfig provisioningConnectorConfig1 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig1.setName("ProvisiningConfig1");
        provisioningConnectorConfig1.setProvisioningProperties(new Property[]{property1});
        ProvisioningConnectorConfig provisioningConnectorConfig2 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig2.setName("ProvisiningConfig2");
        provisioningConnectorConfig2.setProvisioningProperties(new Property[]{property2});
        provisioningConnectorConfig2.setEnabled(true);
        provisioningConnectorConfig2.setBlocking(true);
        idp1.setProvisioningConnectorConfigs(new ProvisioningConnectorConfig[]{provisioningConnectorConfig1,
                provisioningConnectorConfig2});

        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setLocalClaimDialect(false);
        claimConfig.setRoleClaimURI("Country");
        claimConfig.setUserClaimURI("Country");
        ClaimMapping claimMapping = ClaimMapping.build("http://wso2.org/claims/country", "Country", "", true);
        claimConfig.setClaimMappings(new ClaimMapping[]{claimMapping});
        Claim remoteClaim = new Claim();
        remoteClaim.setClaimId(0);
        remoteClaim.setClaimUri("Country");
        claimConfig.setIdpClaims(new Claim[]{remoteClaim});
        idp1.setClaimConfig(claimConfig);

        // Initialize Test Identity Provider 2.
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setIdentityProviderName("testIdP2");

        ClaimConfig claimConfig2 = new ClaimConfig();
        claimConfig2.setLocalClaimDialect(true);
        claimConfig2.setRoleClaimURI("http://wso2.org/claims/role");
        claimConfig2.setUserClaimURI("http://wso2.org/claims/fullname");
        ClaimMapping claimMapping2 = new ClaimMapping();
        Claim localClaim2 = new Claim();
        localClaim2.setClaimId(0);
        localClaim2.setClaimUri("http://wso2.org/claims/fullname");
        claimMapping2.setLocalClaim(localClaim2);
        claimConfig2.setClaimMappings(new ClaimMapping[]{claimMapping2});
        idp2.setClaimConfig(claimConfig2);

        // Initialize Test Identity Provider 3.
        IdentityProvider idp3 = new IdentityProvider();
        idp3.setIdentityProviderName("testIdP3");

        return new Object[][]{
                // IDP with PermissionsAndRoleConfig,FederatedAuthenticatorConfigs,ProvisioningConnectorConfigs,Claims.
                {idp1, SAMPLE_TENANT_ID},
                // IDP with Local Cliam Dialect ClaimConfigs.
                {idp2, SAMPLE_TENANT_ID},
                // IDP with Only name.
                {idp3, SAMPLE_TENANT_ID2},
                // IDP with User Defined Federated Authenticator.
                {userDefinedIdP, SAMPLE_TENANT_ID2},
        };
    }

    @Test(dataProvider = "addIdPData")
    public void testAddIdP(IdentityProvider identityProvider, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            idPManagementDAO.addIdP(((IdentityProvider) identityProvider), tenantId);

            int idpId = -1;
            int authnId = -1;
            String query = IdPManagementConstants.SQLQueries.GET_IDP_BY_NAME_SQL;
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, tenantId);
            statement.setInt(2, MultitenantConstants.SUPER_TENANT_ID);
            statement.setString(3, ((IdentityProvider) identityProvider).getIdentityProviderName());
            ResultSet resultSet = statement.executeQuery();
            String resultName = "";
            if (resultSet.next()) {
                resultName = resultSet.getString("NAME");
                idpId = resultSet.getInt("ID");
            }
            statement.close();
            assertEquals(resultName, ((IdentityProvider) identityProvider).getIdentityProviderName());

            // check whether there is a actionId property in the database for user defined fed authenticators.
            if (identityProvider.getDefaultAuthenticatorConfig() instanceof UserDefinedFederatedAuthenticatorConfig) {

                String sqlStmt = IdPManagementConstants.SQLQueries.GET_ALL_IDP_AUTH_SQL;
                PreparedStatement prepStmt1 = connection.prepareStatement(sqlStmt);
                prepStmt1.setInt(1, idpId);
                ResultSet rs = prepStmt1.executeQuery();

                while (rs.next()) {
                    authnId = rs.getInt("ID");
                }
                String sqlStmt1 = IdPManagementConstants.SQLQueries.GET_IDP_AUTH_PROPS_SQL;
                PreparedStatement prepStmt2 = connection.prepareStatement(sqlStmt1);
                prepStmt2.setInt(1, authnId);
                ResultSet proprs = prepStmt2.executeQuery();
                Set<Property> properties = new HashSet<Property>();
                while (proprs.next()) {
                    Property property = new Property();
                    property.setName(proprs.getString("PROPERTY_KEY"));
                    property.setValue(proprs.getString("PROPERTY_VALUE"));
                    if ((IdPManagementConstants.IS_TRUE_VALUE).equals(proprs.getString("IS_SECRET"))) {
                        property.setConfidential(true);
                    }
                    properties.add(property);
                }
                statement.close();
                assertEquals(properties.size(), 1);
                assertEquals(properties.iterator().next().getName(), "actionId");
                assertEquals(properties.iterator().next().getValue(), ASSOCIATED_ACTION_ID);
            }
        }
    }

    @Test(dataProvider = "addIdPData")
    public void testAddIdPException(Object identityProvider, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.addIdP(((IdentityProvider) identityProvider), tenantId));
        }
    }

    @DataProvider
    public Object[][] getAllFederatedData() {

        return new Object[][]{
                {1, 1},
                {2, 0}
        };
    }

    @Test(dataProvider = "getAllFederatedData")
    public void testGetAllUserDefinedFederatedAuthenticators(int tenantId, int numberOfFederatedAuthenticators)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();
            List<FederatedAuthenticatorConfig> actualAuthenticators = idPManagementDAO
                    .getAllUserDefinedFederatedAuthenticators(tenantId);

            assertEquals(actualAuthenticators.size(), numberOfFederatedAuthenticators);
        }
    }

    @DataProvider
    public Object[][] getPermissionsAndRoleConfigurationData() {

        return new Object[][]{
                {"testIdP1", 1, SAMPLE_TENANT_ID, 2},
                {"testIdP2", 2, SAMPLE_TENANT_ID, 0},
        };
    }

    @Test(dataProvider = "getPermissionsAndRoleConfigurationData")
    public void testGetPermissionsAndRoleConfiguration(String idpName, int idpId, int tenantId, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            PermissionsAndRoleConfig pac = idPManagementDAO.getPermissionsAndRoleConfiguration(connection, idpName,
                    idpId, tenantId);
            assertEquals(pac.getIdpRoles().length, resultCount);
        }
    }

    @DataProvider
    public Object[][] getTestIdpGroupsData() {

        return new Object[][]{
                {1, 2},
                {2, 1},
                {3, 0},
        };
    }

    @Test(dataProvider = "getTestIdpGroupsData")
    public void testGetIdPGroupConfiguration(int idpId, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdPGroup[] idpGroups = idPManagementDAO.getIdPGroupConfiguration(connection, idpId);
            assertEquals(idpGroups.length, resultCount);
        }
    }

    @Test
    public void testUpdateIdPGroupName() throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdPGroup[] idpGroups = idPManagementDAO.getIdPGroupConfiguration(connection, 1);
            assertEquals(idpGroups.length, 2);
            assertEquals(idpGroups[0].getIdpGroupName(), IDP_GROUP1);

            IdPGroup[] newIdpGroups = idpGroups.clone();
            newIdpGroups[0].setIdpGroupName("idpGroup1Updated");
            newIdpGroups[1].setIdpGroupName("idpGroup2Updated");
            newIdpGroups[1].setIdpGroupId("idpGroup2IdUpdated");

            IdentityProvider currentIdP = idPManagementDAO.getIdPByName(connection, "testIdP1",
                    SAMPLE_TENANT_ID, TENANT_DOMAIN);
            IdentityProvider newIdP = idPManagementDAO.getIdPByName(connection, "testIdP1",
                    SAMPLE_TENANT_ID, TENANT_DOMAIN);
            newIdP.setIdPGroupConfig(newIdpGroups);
            idPManagementDAO.updateIdP(newIdP, currentIdP, SAMPLE_TENANT_ID);

            IdPGroup[] updatedIdpGroups = idPManagementDAO.getIdPGroupConfiguration(connection, 1);
            assertEquals(updatedIdpGroups.length, 2);
            assertEquals(updatedIdpGroups[0].getIdpGroupName(), "idpGroup1Updated");
            assertEquals(updatedIdpGroups[1].getIdpGroupName(), "idpGroup2Updated");
            assertNotEquals(updatedIdpGroups[1].getIdpGroupId(), "idpGroup2IdUpdated");
        }
    }

    @DataProvider
    public Object[][] getProvisioningConnectorConfigsData() {

        return new Object[][]{
                {"testIdP1", 1, SAMPLE_TENANT_ID, 2},
                {"testIdP2", 2, SAMPLE_TENANT_ID, 0},
        };
    }

    @Test(dataProvider = "getProvisioningConnectorConfigsData")
    public void testGetProvisioningConnectorConfigs(String idpName, int idpId, int tenantId, int resultCount)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            ProvisioningConnectorConfig[] pccResult = idPManagementDAO.getProvisioningConnectorConfigs(connection,
                    idpName, idpId, tenantId);
            assertEquals(pccResult.length, resultCount);
        }
    }

    @DataProvider
    public Object[][] getIdPByNameData() {

        return new Object[][]{
                {"testIdP1", SAMPLE_TENANT_ID, true},
                {"testIdP3", SAMPLE_TENANT_ID2, true},
                {"testIdP4", SAMPLE_TENANT_ID, false},
                {"notExist", SAMPLE_TENANT_ID, false},
        };
    }

    @Test(dataProvider = "getIdPByNameData")
    public void testGetIdPByName(String idpName, int tenantId, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIdPByName(connection, idpName, tenantId, TENANT_DOMAIN);
            assertIdPResult(idpResult, idpName, isExist);
        }
    }

    @Test
    public void testDeleteIdpProperties() throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider addedIdp = idPManagementDAO.getIdPByName(
                    connection, "testIdP1", SAMPLE_TENANT_ID, TENANT_DOMAIN);
            List<String> propertiesToDelete = new ArrayList<>();
            propertiesToDelete.add(IdPManagementConstants.EMAIL_OTP_PASSWORD_RECOVERY_PROPERTY);

            idPManagementDAO.deleteIdpProperties(Integer.parseInt(addedIdp.getId()), propertiesToDelete, TENANT_DOMAIN);
        }

        assertDeletedIdpProperties();
    }

    private void assertDeletedIdpProperties() throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            IdentityProvider updatedIdp = idPManagementDAO.getIdPByName(
                    connection, "testIdP1", SAMPLE_TENANT_ID, TENANT_DOMAIN);
            assertEquals(updatedIdp.getIdpProperties().length, 3);
            assertFalse(Arrays.stream(updatedIdp.getIdpProperties()).anyMatch( prop ->
                            IdPManagementConstants.EMAIL_OTP_PASSWORD_RECOVERY_PROPERTY.equals(prop.getName())),
                    "Property deletion failed for: " + IdPManagementConstants.EMAIL_OTP_PASSWORD_RECOVERY_PROPERTY);
        }
    }

    @DataProvider
    public Object[][] getIdPByIdData() {

        return new Object[][]{
                {"testIdP1", 1, SAMPLE_TENANT_ID, true},
                {"testIdP3", 3, SAMPLE_TENANT_ID2, true},
                {"notExist", 4, SAMPLE_TENANT_ID, false},
                {userDefinedIdP.getIdentityProviderName(), 4, SAMPLE_TENANT_ID2, true},
        };
    }

    @Test(dataProvider = "getIdPByIdData")
    public void testGetIdPById(String idpName, int idpId, int tenantId, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIDPbyId(connection, idpId, tenantId, TENANT_DOMAIN);
            assertIdPResult(idpResult, idpName, isExist);
        }
    }

    @DataProvider
    public Object[][] getIDPbyResourceIdData() {

        return new Object[][]{
                {"testIdP1", SAMPLE_TENANT_ID, true},
                {"testIdP3", SAMPLE_TENANT_ID2, true},
                {"notExist", SAMPLE_TENANT_ID, false},
                {userDefinedIdP.getIdentityProviderName(), SAMPLE_TENANT_ID2, true}
        };
    }

    @Test(dataProvider = "getIDPbyResourceIdData")
    public void testGetIDPbyResourceId(String idpName, int tenantId, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(()->IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            String uuid = "";
            if (isExist) {
                IdentityProvider result = idPManagementDAO.getIdPByName(connection, idpName, tenantId, TENANT_DOMAIN);
                uuid = result.getResourceId();
            }

            IdentityProvider idpResult = idPManagementDAO.getIDPbyResourceId(connection, uuid, tenantId, TENANT_DOMAIN);
            assertIdPResult(idpResult, idpName, isExist);
        }
    }

    @DataProvider
    public Object[][] getIdPByRealmIdData() {

        return new Object[][]{
                {"testIdP1", "1", SAMPLE_TENANT_ID, true},
                {"testIdP2", "2", SAMPLE_TENANT_ID, true},
                {"notExist", "99", SAMPLE_TENANT_ID2, false},
        };
    }

    @Test(dataProvider = "getIdPByRealmIdData")
    public void testGetIdPByRealmId(String idpName, String realmId, int tenantId, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(()->IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIdPByRealmId(realmId, tenantId, TENANT_DOMAIN);

            if (isExist) {
                assertEquals(idpResult.getIdentityProviderName(), idpName, "'getIDPbyRealmId' method fails");
            } else {
                assertNull(idpResult, "'getIDPbyRealmId' method fails");
            }
        }
    }

    @DataProvider
    public Object[][] getEnabledIdPByRealmIdData() {

        return new Object[][]{
                {"testIdP4", "4", SAMPLE_TENANT_ID3, true, true},
                {"testIdP5", "5", SAMPLE_TENANT_ID3, true, false},
                {"notExist", "99", SAMPLE_TENANT_ID3, false, false},
        };
    }

    @Test(dataProvider = "getEnabledIdPByRealmIdData")
    public void testGetEnabledIdPRealmId(String idpName, String realmId, int tenantId, boolean isExist, boolean isEnabled) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps(connection);

            IdentityProvider idpResult = idPManagementDAO.getEnabledIdPByRealmId(realmId, tenantId, TENANT_DOMAIN);
            if (isExist && isEnabled) {
                assertEquals(idpResult.getIdentityProviderName(), idpName, "'getEnabledIdPByRealmId' method fails");
            } else {
                assertNull(idpResult, "'getEnabledIdPByRealmId' method fails");
            }
        }
    }

    @Test(dataProvider = "getIdPByNameData")
    public void testGetIDPNameByResourceId(String idpName, int tenantId, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIdPByName(connection, idpName, tenantId, TENANT_DOMAIN);
            String uuid = "";
            if (isExist) {
                uuid = idpResult.getResourceId();
            }

            String outputName = idPManagementDAO.getIDPNameByResourceId(uuid);
            if (isExist) {
                assertEquals(outputName, idpName);
            } else {
                assertNull(outputName);
            }
        }
    }

    @DataProvider
    public Object[][] getIdPNameByMetadataPropertyData() {

        return new Object[][]{
                {"testIdP1", "idpPropertyName", "idpPropertyValue", SAMPLE_TENANT_ID},
                {null, "notExist", "4", SAMPLE_TENANT_ID2},
        };
    }

    @Test(dataProvider = "getIdPNameByMetadataPropertyData")
    public void testGetIdPNameByMetadataProperty(String idpName, String property, String value, int tenantId)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            String outputName = idPManagementDAO.getIdPNameByMetadataProperty(connection, property, value, tenantId);
            assertEquals(outputName, idpName);
            String outputName1 = idPManagementDAO.getIdPNameByMetadataProperty(null, property, value, tenantId);
            assertEquals(outputName1, idpName);
        }
    }

    @DataProvider
    public Object[][] getIdPByAuthenticatorPropertyValueData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "testIdP1", "Property1", "value1", "Name", true},
                {SAMPLE_TENANT_ID, "testIdP1", "Property2", "value2", "Name", true},
                {SAMPLE_TENANT_ID, "NotExist", "Null", "Null", "Null", false},
        };
    }

    @Test(dataProvider = "getIdPByAuthenticatorPropertyValueData")
    public void testGetIdPByAuthenticatorPropertyValue(int tenantId, String idpName, String property, String value,
                                                       String authenticator, boolean isExist) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIdPByAuthenticatorPropertyValue(connection, property,
                    value, authenticator, tenantId, TENANT_DOMAIN);
            if (isExist) {
                assertEquals(idpResult.getIdentityProviderName(), idpName);
            } else {
                assertNull(idpResult);
            }

        }
    }

    @Test(dataProvider = "getIdPByAuthenticatorPropertyValueData")
    public void testGetIdPByAuthenticatorPropertyValueException(int tenantId, String idpName, String property,
                                                                String value, String authenticator, boolean isExist)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.getIdPByAuthenticatorPropertyValue(null, property, value, authenticator,
                            tenantId, TENANT_DOMAIN));
        }
    }

    @DataProvider
    public Object[][] getIdPByAuthenticatorPropertyValueWithoutAuthenticatorData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "testIdP1", "Property1", "value1", true},
                {SAMPLE_TENANT_ID, "testIdP1", "Property2", "value2", true},
                {SAMPLE_TENANT_ID, "NotExist", "Null", "Null", false},
        };
    }

    @Test(dataProvider = "getIdPByAuthenticatorPropertyValueWithoutAuthenticatorData")
    public void testGetIdPByAuthenticatorPropertyWithoutAuthenticatorData(int tenantId, String idpName, String property,
                                                                          String value, boolean isExist)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idpResult = idPManagementDAO.getIdPByAuthenticatorPropertyValue(connection, property,
                    value, tenantId, TENANT_DOMAIN);

            if (isExist) {
                assertEquals(idpResult.getIdentityProviderName(), idpName);
            } else {
                assertNull(idpResult);
            }
        }
    }

    @DataProvider
    public Object[][] updateIdPData() {

        // Initialize Test Identity Provider 1.
        IdentityProvider idp1 = new IdentityProvider();
        idp1.setIdentityProviderName("testIdP1");
        idp1.setHomeRealmId("1");
        idp1.setEnable(true);
        idp1.setPrimary(true);
        idp1.setFederationHub(true);
        idp1.setCertificate("");

        FederatedAuthenticatorConfig federatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
        federatedAuthenticatorConfig.setDisplayName("DisplayName1");
        federatedAuthenticatorConfig.setName("Name");
        federatedAuthenticatorConfig.setEnabled(true);
        Property property1 = new Property();
        property1.setName("Property1");
        property1.setValue("value1");
        property1.setConfidential(true);
        federatedAuthenticatorConfig.setProperties(new Property[]{property1});
        idp1.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{federatedAuthenticatorConfig});

        ProvisioningConnectorConfig provisioningConnectorConfig = new ProvisioningConnectorConfig();
        provisioningConnectorConfig.setName("ProvisiningConfig1");
        provisioningConnectorConfig.setProvisioningProperties(new Property[]{property1});
        idp1.setProvisioningConnectorConfigs(new ProvisioningConnectorConfig[]{provisioningConnectorConfig});

        // Initialize Test Identity Provider 2.
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setIdentityProviderName("testIdP2");
        idp2.setHomeRealmId("2");

        ClaimConfig claimConfig2 = new ClaimConfig();
        claimConfig2.setLocalClaimDialect(true);
        claimConfig2.setRoleClaimURI("http://wso2.org/claims/role");
        claimConfig2.setUserClaimURI("http://wso2.org/claims/fullname");
        ClaimMapping claimMapping2 = new ClaimMapping();
        Claim localClaim2 = new Claim();
        localClaim2.setClaimId(0);
        localClaim2.setClaimUri("http://wso2.org/claims/fullname");
        claimMapping2.setLocalClaim(localClaim2);
        claimConfig2.setClaimMappings(new ClaimMapping[]{claimMapping2});
        idp2.setClaimConfig(claimConfig2);

        // Initialize Test Identity Provider 3.
        IdentityProvider idp3 = new IdentityProvider();
        idp3.setIdentityProviderName("testIdP3");

        // Initialize New Test Identity Provider 1.
        IdentityProvider idp1New = new IdentityProvider();
        idp1New.setIdentityProviderName("testIdP1New");
        idp1New.setEnable(true);
        idp1New.setPrimary(true);
        idp1New.setFederationHub(true);
        idp1New.setCertificate("");

        RoleMapping newRoleMapping1 = new RoleMapping();
        newRoleMapping1.setRemoteRole("Role1New");
        newRoleMapping1.setLocalRole(new LocalRole("1", "LocalRole1"));
        RoleMapping newRoleMapping2 = new RoleMapping();
        newRoleMapping2.setRemoteRole("Role2New");
        newRoleMapping2.setLocalRole(new LocalRole("2", "LocalRole2"));

        PermissionsAndRoleConfig newPermissionsAndRoleConfig = new PermissionsAndRoleConfig();
        newPermissionsAndRoleConfig.setIdpRoles(new String[]{"Role1New", "Role2New"});
        newPermissionsAndRoleConfig.setRoleMappings(new RoleMapping[]{newRoleMapping1, newRoleMapping2});
        idp1New.setPermissionAndRoleConfig(newPermissionsAndRoleConfig);

        FederatedAuthenticatorConfig newFederatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
        newFederatedAuthenticatorConfig.setDisplayName("DisplayName1New");
        newFederatedAuthenticatorConfig.setName("Name");
        newFederatedAuthenticatorConfig.setEnabled(true);
        newFederatedAuthenticatorConfig.setDefinedByType(DefinedByType.SYSTEM);
        Property property1New = new Property();
        property1New.setName("Property1New");
        property1New.setValue("value1New");
        property1New.setConfidential(false);
        Property property2New = new Property();
        property2New.setName("Property2New");
        property2New.setValue("value2New");
        property2New.setConfidential(false);
        newFederatedAuthenticatorConfig.setProperties(new Property[]{property1New, property2New});
        idp1New.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{newFederatedAuthenticatorConfig});

        ProvisioningConnectorConfig newProvisioningConnectorConfig1 = new ProvisioningConnectorConfig();
        newProvisioningConnectorConfig1.setName("ProvisiningConfig1");
        newProvisioningConnectorConfig1.setProvisioningProperties(new Property[]{property1New});
        ProvisioningConnectorConfig newProvisioningConnectorConfig2 = new ProvisioningConnectorConfig();
        newProvisioningConnectorConfig2.setName("ProvisiningConfig2");
        newProvisioningConnectorConfig2.setProvisioningProperties(new Property[]{property2New});
        newProvisioningConnectorConfig2.setEnabled(true);
        newProvisioningConnectorConfig2.setBlocking(true);
        idp1New.setProvisioningConnectorConfigs(new ProvisioningConnectorConfig[]{newProvisioningConnectorConfig1,
                newProvisioningConnectorConfig2});

        JustInTimeProvisioningConfig justInTimeProvisioningConfig = new JustInTimeProvisioningConfig();
        justInTimeProvisioningConfig.setProvisioningUserStore("PRIMARY");
        idp1New.setJustInTimeProvisioningConfig(justInTimeProvisioningConfig);

        ClaimConfig newClaimConfig = new ClaimConfig();
        newClaimConfig.setLocalClaimDialect(false);
        newClaimConfig.setRoleClaimURI("Country");
        newClaimConfig.setUserClaimURI("Country");
        ClaimMapping claimMapping = ClaimMapping.build("http://wso2.org/claims/country", "Country", "", true);
        Claim remoteClaim = new Claim();
        remoteClaim.setClaimId(0);
        remoteClaim.setClaimUri("Country");
        newClaimConfig.setClaimMappings(new ClaimMapping[]{claimMapping});
        newClaimConfig.setIdpClaims(new Claim[]{remoteClaim});
        idp1New.setClaimConfig(newClaimConfig);

        // Initialize New Test Identity Provider 2.
        IdentityProvider idp2New = new IdentityProvider();
        idp2New.setIdentityProviderName("testIdP2New");

        // Initialize New Test Identity Provider 3.
        IdentityProvider idp3New = new IdentityProvider();
        idp3New.setIdentityProviderName("testIdP3New");

        IdentityProvider userDefinedIdPToBeUpdated = ActionMgtTestUtil
                .createIdPWithUserDefinedFederatedAuthenticatorConfig(CUSTOM_IDP_NAME + "new", ActionMgtTestUtil.createEndpointConfig("http://localhostnew1", "adminnew1", "adminnew1"));

        return new Object[][]{
                // Update PermissionsAndRoleConfig,FederatedAuthenticatorConfig,ProvisioningConnectorConfig,ClaimConfig.
                {idp1, idp1New, SAMPLE_TENANT_ID},
                // Update name, LocalClaimDialect, ClaimConfig.
                {idp2, idp2New, SAMPLE_TENANT_ID},
                // Update name.
                {idp3, idp3New, SAMPLE_TENANT_ID2},
                // IDP with User Defined Federated Authenticator.
                {userDefinedIdP, userDefinedIdPToBeUpdated, SAMPLE_TENANT_ID2},
        };
    }

    @Test(dataProvider = "updateIdPData")
    public void testUpdateIdP(Object oldIdp, Object newIdp, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             MockedStatic<IdentityUtil> identityUtil = mockStatic(IdentityUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityUtil.when(() -> IdentityUtil.getProperty(RESET_PROVISIONING_ENTITIES_ON_CONFIG_UPDATE))
                    .thenReturn("false");
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            addTestIdps();
            idPManagementDAO.updateIdP((IdentityProvider) newIdp, ((IdentityProvider) oldIdp), tenantId);

            String newIdpName = ((IdentityProvider) newIdp).getIdentityProviderName();
            IdentityProvider idpResult = idPManagementDAO.getIdPByName(connection, newIdpName, tenantId, TENANT_DOMAIN);
            assertIdPResult(idpResult, newIdpName, true);
            assertEquals(idpResult.getIdentityProviderName(), newIdpName);
        }
    }

    @Test(dataProvider = "updateIdPData")
    public void testUpdateIdPException(Object oldIdp, Object newIdp, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             MockedStatic<IdentityUtil> identityUtil = mockStatic(IdentityUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityUtil.when(() -> IdentityUtil.getProperty(RESET_PROVISIONING_ENTITIES_ON_CONFIG_UPDATE))
                    .thenReturn("false");

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.updateIdP((IdentityProvider) newIdp, ((IdentityProvider) oldIdp), tenantId));
        }
    }

    @DataProvider
    public Object[][] deleteIdPData() {

        return new Object[][]{
                {"testIdP1", SAMPLE_TENANT_ID},
                {"testIdP3", SAMPLE_TENANT_ID2},
                {userDefinedIdP.getIdentityProviderName(), SAMPLE_TENANT_ID2},
        };
    }

    @Test(dataProvider = "deleteIdPData")
    public void testDeleteIdP(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            idPManagementDAO.deleteIdP(idpName, tenantId, TENANT_DOMAIN);
            int resultSize = getIdPCount(connection, idpName, tenantId);
            assertEquals(resultSize, 0, "'deleteIdP' method fails");
        }
    }

    @DataProvider
    public Object[][] deleteIdPExceptionData() {

        return new Object[][]{
                {"notExist", SAMPLE_TENANT_ID},
        };
    }

    @Test(dataProvider = "deleteIdPExceptionData")
    public void testDeleteIdPException(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.deleteIdP(idpName, tenantId, TENANT_DOMAIN));
        }
    }

    @Test(dataProvider = "deleteIdPData")
    public void testDeleteIdPByResourceId(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            String uuid = idPManagementDAO.getIdPByName(connection, idpName, tenantId, TENANT_DOMAIN).getResourceId();
            idPManagementDAO.deleteIdPByResourceId(uuid, tenantId, TENANT_DOMAIN);
            int resultSize = getIdPCount(connection, idpName, tenantId);
            assertEquals(resultSize, 0, "'deleteIdPByResourceId' method fails");
        }
    }

    @Test(dataProvider = "deleteIdPExceptionData")
    public void testDeleteIdPByResourceIdException(String uuid, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.deleteIdPByResourceId(uuid, tenantId, TENANT_DOMAIN));
        }
    }

    @Test(dataProvider = "deleteIdPData")
    public void testForceDeleteIdP(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            idPManagementDAO.forceDeleteIdP(idpName, tenantId, TENANT_DOMAIN);
            int resultSize = getIdPCount(connection, idpName, tenantId);
            assertEquals(resultSize, 0, "'forceDeleteIdP' method fails");
        }
    }

    @Test(dataProvider = "deleteIdPExceptionData")
    public void testForceDeleteIdPException(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(()->IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.forceDeleteIdP(idpName, tenantId, TENANT_DOMAIN));
        }
    }

    @Test(dataProvider = "deleteIdPData")
    public void testForceDeleteIdPByResourceId(String idpName, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            String uuid = idPManagementDAO.getIdPByName(connection, idpName, tenantId, TENANT_DOMAIN).getResourceId();
            idPManagementDAO.forceDeleteIdPByResourceId(uuid, tenantId, TENANT_DOMAIN);
            int resultSize = getIdPCount(connection, idpName, tenantId);
            assertEquals(resultSize, 0, "'forceDeleteIdPByResourceId' method fails");
        }
    }

    @Test(dataProvider = "deleteIdPExceptionData")
    public void testForceDeleteIdPByResourceIdException(String uuid, int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.forceDeleteIdPByResourceId(uuid, tenantId, TENANT_DOMAIN));
        }
    }

    @DataProvider
    public Object[][] deleteIdPsData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID},
                {SAMPLE_TENANT_ID2},
        };
    }

    @Test(dataProvider = "deleteIdPsData")
    public void testDeleteIdPs(int tenantId) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(()->IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            addTestIdps();
            idPManagementDAO.deleteIdPs(tenantId);
        }

        try (Connection connection = getConnection(DB_NAME)) {
            String query = IdPManagementConstants.SQLQueries.GET_IDPS_SQL;
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, tenantId);
            statement.setInt(2, MultitenantConstants.SUPER_TENANT_ID);
            ResultSet resultSet = statement.executeQuery();
            int resultSize = 0;
            if (resultSet.next()) {
                resultSize = resultSet.getRow();
            }
            statement.close();
            assertEquals(resultSize, 0);
        }
    }

    @DataProvider
    public Object[][] deleteTenantRoleData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "Role1"},
                {SAMPLE_TENANT_ID, "Role2"},
        };
    }

    @Test(dataProvider = "deleteTenantRoleData")
    public void testDeleteTenantRole(int tenantId, String role) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            addTestIdps();
            idPManagementDAO.deleteTenantRole(tenantId, role, TENANT_DOMAIN);
        }

        try (Connection connection = getConnection(DB_NAME)) {
            String query = "SELECT * FROM IDP_ROLE WHERE TENANT_ID=? AND ROLE=?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, tenantId);
            statement.setString(2, role);
            ResultSet resultSet = statement.executeQuery();
            int resultSize = 0;
            if (resultSet.next()) {
                resultSize = resultSet.getRow();
            }
            statement.close();
            assertEquals(resultSize, 0);
        }
    }

    @Test(dataProvider = "deleteTenantRoleData")
    public void testDeleteTenantRoleException(int tenantId, String role) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean()))
                        .thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.deleteTenantRole(tenantId, role, TENANT_DOMAIN));
        }
    }

    @DataProvider
    public Object[][] renameTenantRoleData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "NewLocalRole1", "LocalRole1", 1},
                {SAMPLE_TENANT_ID, "NewLocalRole2", "LocalRole2", 1},
                {SAMPLE_TENANT_ID, "NewLocalRole2", "2", 0},
        };
    }

    @Test(dataProvider = "renameTenantRoleData")
    public void testRenameTenantRole(int tenantId, String newRole, String oldRole, int count) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            addTestIdps();
            idPManagementDAO.renameTenantRole(newRole, oldRole, tenantId, TENANT_DOMAIN);

            String query = "SELECT * FROM IDP_ROLE_MAPPING WHERE TENANT_ID=? AND LOCAL_ROLE=?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, tenantId);
            statement.setString(2, newRole);
            ResultSet resultSet = statement.executeQuery();
            int resultSize = 0;
            if (resultSet.next()) {
                resultSize = resultSet.getRow();
            }
            statement.close();
            assertEquals(resultSize, count);
        }
    }

    @Test(dataProvider = "renameTenantRoleData")
    public void testRenameTenantRoleException(int tenantId, String newRole, String oldRole, int count)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean()))
                        .thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

                addTestIdps();
                idPManagementDAO.renameTenantRole(newRole, oldRole, tenantId, TENANT_DOMAIN);
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.renameTenantRole(newRole, oldRole, tenantId, TENANT_DOMAIN));
        }
    }

    @DataProvider
    public Object[][] renameClaimURIData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "newClaimURI", "http://wso2.org/claims/country", 1},
                {4, "newClaimURI", "oldClaimURI", 0},
        };
    }

    @Test(dataProvider = "renameClaimURIData")
    public void testRenameClaimURI(int tenantId, String newClaimURI, String oldClaimURI, int count) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));

            addTestIdps();
            idPManagementDAO.renameClaimURI(newClaimURI, oldClaimURI, tenantId, TENANT_DOMAIN);

            String query = "SELECT * FROM IDP_CLAIM_MAPPING WHERE TENANT_ID=? AND LOCAL_CLAIM=?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, tenantId);
            statement.setString(2, newClaimURI);
            ResultSet resultSet = statement.executeQuery();
            int resultSize = 0;
            if (resultSet.next()) {
                resultSize = resultSet.getRow();
            }
            statement.close();
            assertEquals(resultSize, count);
        }
    }

    @Test(dataProvider = "renameClaimURIData")
    public void testRenameClaimURIException(int tenantId, String newClaimURI, String oldClaimURI, int count)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean()))
                        .thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.renameClaimURI(newClaimURI, oldClaimURI, tenantId, TENANT_DOMAIN));
        }
    }

    @DataProvider
    public Object[][] isIdPAvailableForAuthenticatorPropertyData() {

        return new Object[][]{
                {SAMPLE_TENANT_ID, "Name", "Property1", "value1", true},
                {SAMPLE_TENANT_ID, "Address", "Property2", "value2", false},
        };
    }

    @Test(dataProvider = "isIdPAvailableForAuthenticatorPropertyData")
    public void testIsIdPAvailableForAuthenticatorProperty(int tenantId, String authenticator, String property,
                                                           String value, boolean isAvailable) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            boolean availabilityResult = idPManagementDAO.isIdPAvailableForAuthenticatorProperty(authenticator,
                    property, value, tenantId);
            assertEquals(availabilityResult, isAvailable);
        }
    }

    @Test(dataProvider = "isIdPAvailableForAuthenticatorPropertyData")
    public void testIsIdPAvailableForAuthenticatorPropertyException(int tenantId, String authenticator, String property,
                                                                    String value, boolean isAvailable)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean()))
                        .thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.isIdPAvailableForAuthenticatorProperty(authenticator, property, value, tenantId));
        }
    }

    @DataProvider
    public Object[][] isIdpReferredBySPData() {

        return new Object[][]{
                {"testIdP1", SAMPLE_TENANT_ID, false},
                {"testIdP2", SAMPLE_TENANT_ID, false},
        };
    }

    @Test(dataProvider = "isIdpReferredBySPData")
    public void testIsIdpReferredBySP(String idPName, int tenantId, boolean isAvailable) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            boolean availabilityResult = idPManagementDAO.isIdpReferredBySP(idPName, tenantId);
            assertEquals(availabilityResult, isAvailable);
        }
    }

    @Test(dataProvider = "isIdpReferredBySPData")
    public void testIsIdpReferredBySPException(String idPName, int tenantId, boolean isAvailable) throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class)) {
            try (Connection connection = getConnection(DB_NAME)) {
                identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
                identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
                addTestIdps();
            }
            assertThrows(IdentityProviderManagementException.class, () ->
                    idPManagementDAO.isIdpReferredBySP(idPName, tenantId));
        }
    }

    @DataProvider
    public Object[][] getConnectedApplicationsData() {

        return new Object[][]{
                {"testIdP1", SAMPLE_TENANT_ID, 2, 2, 0},
                {"testIdP2", SAMPLE_TENANT_ID, 1, 1, 0},
        };
    }

    @Test(dataProvider = "getConnectedApplicationsData")
    public void testGetConnectedApplications(String idPName, int tenantId, int limit, int offset, int count)
            throws Exception {

        try (MockedStatic<IdentityDatabaseUtil> identityDatabaseUtil = mockStatic(IdentityDatabaseUtil.class);
             Connection connection = getConnection(DB_NAME)) {
            identityDatabaseUtil.when(() -> IdentityDatabaseUtil.getDBConnection(anyBoolean())).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDBConnection).thenReturn(connection);
            identityDatabaseUtil.when(IdentityDatabaseUtil::getDataSource).thenReturn(dataSourceMap.get(DB_NAME));
            addTestIdps();

            IdentityProvider idp = idPManagementDAO.getIdPByName(connection, idPName, tenantId, TENANT_DOMAIN);
            String uuid = idp.getResourceId();

            ConnectedAppsResult result = idPManagementDAO.getConnectedApplications(uuid, limit, offset);
            assertEquals(result.getTotalAppCount(), count);
        }
    }

    private void addTestIdps() throws IdentityProviderManagementException {

        // Initialize Test Identity Provider 1.
        IdentityProvider idp1 = new IdentityProvider();
        idp1.setIdentityProviderName("testIdP1");
        idp1.setHomeRealmId("1");
        idp1.setEnable(true);
        idp1.setPrimary(true);
        idp1.setFederationHub(true);
        idp1.setCertificate("");

        RoleMapping roleMapping1 = new RoleMapping();
        roleMapping1.setRemoteRole("Role1");
        roleMapping1.setLocalRole(new LocalRole("1", "LocalRole1"));
        RoleMapping roleMapping2 = new RoleMapping();
        roleMapping2.setRemoteRole("Role2");
        roleMapping2.setLocalRole(new LocalRole("2", "LocalRole2"));

        PermissionsAndRoleConfig permissionsAndRoleConfig = new PermissionsAndRoleConfig();
        permissionsAndRoleConfig.setIdpRoles(new String[]{"Role1", "Role2"});
        permissionsAndRoleConfig.setRoleMappings(new RoleMapping[]{roleMapping1, roleMapping2});
        idp1.setPermissionAndRoleConfig(permissionsAndRoleConfig);

        FederatedAuthenticatorConfig federatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
        federatedAuthenticatorConfig.setDisplayName("DisplayName1");
        federatedAuthenticatorConfig.setName("Name");
        federatedAuthenticatorConfig.setEnabled(true);
        federatedAuthenticatorConfig.setDefinedByType(DefinedByType.SYSTEM);
        Property property1 = new Property();
        property1.setName("Property1");
        property1.setValue("value1");
        property1.setConfidential(true);
        Property property2 = new Property();
        property2.setName("Property2");
        property2.setValue("value2");
        property2.setConfidential(false);
        federatedAuthenticatorConfig.setProperties(new Property[]{property1, property2});
        idp1.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{federatedAuthenticatorConfig});

        ProvisioningConnectorConfig provisioningConnectorConfig1 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig1.setName("ProvisiningConfig1");
        provisioningConnectorConfig1.setProvisioningProperties(new Property[]{property1});
        ProvisioningConnectorConfig provisioningConnectorConfig2 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig2.setName("ProvisiningConfig2");
        provisioningConnectorConfig2.setProvisioningProperties(new Property[]{property2});
        provisioningConnectorConfig2.setEnabled(true);
        provisioningConnectorConfig2.setBlocking(true);
        idp1.setProvisioningConnectorConfigs(new ProvisioningConnectorConfig[]{provisioningConnectorConfig1,
                provisioningConnectorConfig2});

        IdentityProviderProperty identityProviderProperty = new IdentityProviderProperty();
        identityProviderProperty.setDisplayName("idpDisplayName");
        identityProviderProperty.setName("idpPropertyName");
        identityProviderProperty.setValue("idpPropertyValue");

        IdentityProviderProperty idpProp2 = new IdentityProviderProperty();
        idpProp2.setValue("true");
        idpProp2.setName(IdPManagementConstants.EMAIL_OTP_PASSWORD_RECOVERY_PROPERTY);
        idp1.setIdpProperties(new IdentityProviderProperty[]{identityProviderProperty, idpProp2});

        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setLocalClaimDialect(false);
        claimConfig.setRoleClaimURI("Country");
        claimConfig.setUserClaimURI("Country");
        ClaimMapping claimMapping = ClaimMapping.build("http://wso2.org/claims/country", "Country", "", true);
        Claim remoteClaim = new Claim();
        remoteClaim.setClaimId(0);
        remoteClaim.setClaimUri("Country");
        claimConfig.setClaimMappings(new ClaimMapping[]{claimMapping});
        claimConfig.setIdpClaims(new Claim[]{remoteClaim});
        idp1.setClaimConfig(claimConfig);

        IdPGroup idPGroup1 = new IdPGroup();
        idPGroup1.setIdpGroupName(IDP_GROUP1);
        IdPGroup idPGroup2 = new IdPGroup();
        idPGroup2.setIdpGroupName(IDP_GROUP2);
        idp1.setIdPGroupConfig(new IdPGroup[]{idPGroup1, idPGroup2});

        // Initialize Test Identity Provider 2.
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setIdentityProviderName("testIdP2");
        idp2.setHomeRealmId("2");

        ClaimConfig claimConfig2 = new ClaimConfig();
        claimConfig2.setLocalClaimDialect(true);
        claimConfig2.setRoleClaimURI("http://wso2.org/claims/role");
        claimConfig2.setUserClaimURI("http://wso2.org/claims/fullname");
        ClaimMapping claimMapping2 = new ClaimMapping();
        Claim localClaim2 = new Claim();
        localClaim2.setClaimId(0);
        localClaim2.setClaimUri("http://wso2.org/claims/fullname");
        claimMapping2.setLocalClaim(localClaim2);
        claimConfig2.setClaimMappings(new ClaimMapping[]{claimMapping2});
        idp2.setClaimConfig(claimConfig2);

        IdPGroup idPGroup3 = new IdPGroup();
        idPGroup3.setIdpGroupName(IDP_GROUP1);
        IdPGroup idPGroup4 = new IdPGroup();
        idPGroup4.setIdpGroupName(IDP_GROUP2);
        idPGroup4.setIdpGroupId(IDP_GROUP2_ID);
        idp2.setIdPGroupConfig(new IdPGroup[]{idPGroup3, idPGroup4});

        // Initialize Test Identity Provider 3.
        IdentityProvider idp3 = new IdentityProvider();
        idp3.setIdentityProviderName("testIdP3");
        idp3.setHomeRealmId("3");

        // IDP with PermissionsAndRoleConfig, FederatedAuthenticatorConfigs, ProvisioningConnectorConfigs, ClaimConfigs.
        idPManagementDAO.addIdP(idp1, SAMPLE_TENANT_ID);
        // IDP with Local Cliam Dialect ClaimConfigs.
        idPManagementDAO.addIdP(idp2, SAMPLE_TENANT_ID);
        // IDP with Only name.
        idPManagementDAO.addIdP(idp3, SAMPLE_TENANT_ID2);
        // IDP with a User defined authenticator.
        idPManagementDAO.addIdP(userDefinedIdP, SAMPLE_TENANT_ID2);
    }

    // Add enabled and disabled IdPs used for tests.
    private void addTestIdps(Connection connection) throws IdentityProviderManagementException {
        // Initialize Test Identity Provider 4.
        IdentityProvider idp4 = new IdentityProvider();
        idp4.setIdentityProviderName("testIdP4");
        idp4.setHomeRealmId("4");

        // Initialize Test Identity Provider 5.
        IdentityProvider idp5 = new IdentityProvider();
        idp5.setIdentityProviderName("testIdP5");
        idp5.setHomeRealmId("5");

        // IDP with Only name.
        idPManagementDAO.addIdP(idp4, SAMPLE_TENANT_ID3);
        // Disabled IDP.
        idPManagementDAO.addIdP(idp5, SAMPLE_TENANT_ID3);
        IdentityProvider tempIdP = idPManagementDAO.getIdPByName(connection, "testIdP5", SAMPLE_TENANT_ID3, TENANT_DOMAIN);
        tempIdP.setEnable(false);
        idPManagementDAO.updateIdP(tempIdP, idp5, SAMPLE_TENANT_ID3);
    }

    private void addTestTrustedTokenIssuers() throws IdentityProviderManagementException {

        // Initialize Test Identity Provider 1.
        IdentityProvider idp1 = new IdentityProvider();
        idp1.setIdentityProviderName("testIdP1");
        idp1.setHomeRealmId("1");
        idp1.setEnable(true);
        idp1.setPrimary(true);
        idp1.setFederationHub(true);
        idp1.setCertificate("");

        RoleMapping roleMapping1 = new RoleMapping();
        roleMapping1.setRemoteRole("Role1");
        roleMapping1.setLocalRole(new LocalRole("1", "LocalRole1"));
        RoleMapping roleMapping2 = new RoleMapping();
        roleMapping2.setRemoteRole("Role2");
        roleMapping2.setLocalRole(new LocalRole("2", "LocalRole2"));

        PermissionsAndRoleConfig permissionsAndRoleConfig = new PermissionsAndRoleConfig();
        permissionsAndRoleConfig.setIdpRoles(new String[]{"Role1", "Role2"});
        permissionsAndRoleConfig.setRoleMappings(new RoleMapping[]{roleMapping1, roleMapping2});
        idp1.setPermissionAndRoleConfig(permissionsAndRoleConfig);

        FederatedAuthenticatorConfig federatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
        federatedAuthenticatorConfig.setDisplayName("DisplayName1");
        federatedAuthenticatorConfig.setName("Name");
        federatedAuthenticatorConfig.setEnabled(true);
        federatedAuthenticatorConfig.setDefinedByType(DefinedByType.SYSTEM);
        Property property1 = new Property();
        property1.setName("Property1");
        property1.setValue("value1");
        property1.setConfidential(true);
        Property property2 = new Property();
        property2.setName("Property2");
        property2.setValue("value2");
        property2.setConfidential(false);
        federatedAuthenticatorConfig.setProperties(new Property[]{property1, property2});
        idp1.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{federatedAuthenticatorConfig});

        ProvisioningConnectorConfig provisioningConnectorConfig1 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig1.setName("ProvisiningConfig1");
        provisioningConnectorConfig1.setProvisioningProperties(new Property[]{property1});
        ProvisioningConnectorConfig provisioningConnectorConfig2 = new ProvisioningConnectorConfig();
        provisioningConnectorConfig2.setName("ProvisiningConfig2");
        provisioningConnectorConfig2.setProvisioningProperties(new Property[]{property2});
        provisioningConnectorConfig2.setEnabled(true);
        provisioningConnectorConfig2.setBlocking(true);
        idp1.setProvisioningConnectorConfigs(new ProvisioningConnectorConfig[]{provisioningConnectorConfig1,
                provisioningConnectorConfig2});

        IdentityProviderProperty identityProviderProperty = new IdentityProviderProperty();
        identityProviderProperty.setDisplayName("idpDisplayName");
        identityProviderProperty.setName("idpPropertyName");
        identityProviderProperty.setValue("idpPropertyValue");
        idp1.setIdpProperties(new IdentityProviderProperty[]{identityProviderProperty});

        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setLocalClaimDialect(false);
        claimConfig.setRoleClaimURI("Country");
        claimConfig.setUserClaimURI("Country");
        ClaimMapping claimMapping = ClaimMapping.build("http://wso2.org/claims/country", "Country", "", true);
        Claim remoteClaim = new Claim();
        remoteClaim.setClaimId(0);
        remoteClaim.setClaimUri("Country");
        claimConfig.setClaimMappings(new ClaimMapping[]{claimMapping});
        claimConfig.setIdpClaims(new Claim[]{remoteClaim});
        idp1.setClaimConfig(claimConfig);

        IdPGroup idPGroup1 = new IdPGroup();
        idPGroup1.setIdpGroupName(IDP_GROUP1);
        IdPGroup idPGroup2 = new IdPGroup();
        idPGroup2.setIdpGroupName(IDP_GROUP2);
        idp1.setIdPGroupConfig(new IdPGroup[]{idPGroup1, idPGroup2});

        idp1.setTrustedTokenIssuer(true);

        // Initialize Test Identity Provider 2.
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setIdentityProviderName("testIdP2");
        idp2.setHomeRealmId("2");

        ClaimConfig claimConfig2 = new ClaimConfig();
        claimConfig2.setLocalClaimDialect(true);
        claimConfig2.setRoleClaimURI("http://wso2.org/claims/role");
        claimConfig2.setUserClaimURI("http://wso2.org/claims/fullname");
        ClaimMapping claimMapping2 = new ClaimMapping();
        Claim localClaim2 = new Claim();
        localClaim2.setClaimId(0);
        localClaim2.setClaimUri("http://wso2.org/claims/fullname");
        claimMapping2.setLocalClaim(localClaim2);
        claimConfig2.setClaimMappings(new ClaimMapping[]{claimMapping2});
        idp2.setClaimConfig(claimConfig2);

        IdPGroup idPGroup3 = new IdPGroup();
        idPGroup3.setIdpGroupName(IDP_GROUP1);
        IdPGroup idPGroup4 = new IdPGroup();
        idPGroup4.setIdpGroupName(IDP_GROUP2);
        idPGroup4.setIdpGroupId(IDP_GROUP2_ID);
        idp2.setIdPGroupConfig(new IdPGroup[]{idPGroup3, idPGroup4});

        // Initialize Test Identity Provider 3.
        IdentityProvider idp3 = new IdentityProvider();
        idp3.setIdentityProviderName("testIdP3");
        idp3.setHomeRealmId("3");

        // IDP with PermissionsAndRoleConfig, FederatedAuthenticatorConfigs, ProvisioningConnectorConfigs, ClaimConfigs.
        idPManagementDAO.addIdP(idp1, SAMPLE_TENANT_ID);
        // IDP with Local Cliam Dialect ClaimConfigs.
        idPManagementDAO.addIdP(idp2, SAMPLE_TENANT_ID);
        // IDP with Only name.
        idPManagementDAO.addIdP(idp3, SAMPLE_TENANT_ID2);
    }

    private int getIdPCount(Connection connection, String idpName, int tenantId) throws SQLException {

        String query = IdPManagementConstants.SQLQueries.GET_IDP_BY_NAME_SQL;
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, tenantId);
        statement.setInt(2, MultitenantConstants.SUPER_TENANT_ID);
        statement.setString(3, (idpName));

        ResultSet resultSet = statement.executeQuery();
        int resultSize = 0;
        if (resultSet.next()) {
            resultSize = resultSet.getRow();
        }
        statement.clearParameters();
        statement.close();
        return resultSize;
    }

    private void assertIdPResult(IdentityProvider idpResult, String idpName, boolean isExist) {

        if (isExist) {
            assertEquals(idpResult.getIdentityProviderName(), idpName, "'getIdPByName' method fails");
            for (FederatedAuthenticatorConfig config : idpResult.getFederatedAuthenticatorConfigs()) {
                if (config instanceof UserDefinedFederatedAuthenticatorConfig) {
                    assertEquals(config.getDefinedByType(), DefinedByType.USER);
                    Property[] prop = idpResult.getDefaultAuthenticatorConfig().getProperties();
                    assertEquals(prop.length, 1);
                    assertEquals(prop[0].getName(), "actionId");
                    assertEquals(prop[0].getValue(), ASSOCIATED_ACTION_ID);
                } else {
                    assertEquals(config.getDefinedByType(), DefinedByType.SYSTEM);
                }
            }
        } else {
            assertNull(idpResult, "'getIdPByName' method fails");
        }
    }
}

