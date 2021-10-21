/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.HomeDashboard;
import org.thingsboard.server.common.data.HomeDashboardInfo;
import org.thingsboard.server.common.data.ShortCustomerInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class DashboardController extends BaseController {

    public static final String DASHBOARD_ID = "dashboardId";

    private static final String HOME_DASHBOARD_ID = "homeDashboardId";
    private static final String HOME_DASHBOARD_HIDE_TOOLBAR = "homeDashboardHideToolbar";
    public static final String DASHBOARD_INFO_DEFINITION = "The Dashboard Info object contains lightweight information about the dashboard (e.g. title, image, assigned customers) but does not contain the heavyweight configuration JSON.";
    public static final String DASHBOARD_DEFINITION = "The Dashboard object is a heavyweight object that contains information about the dashboard (e.g. title, image, assigned customers) and also configuration JSON (e.g. layouts, widgets, entity aliases).";
    public static final String HIDDEN_FOR_MOBILE = "Exclude dashboards that are hidden for mobile";

    @Value("${dashboard.max_datapoints_limit}")
    private long maxDatapointsLimit;

    @ApiOperation(value = "Get server time (getServerTime)",
            notes = "Get the server time (milliseconds since January 1, 1970 UTC). " +
                    "Used to adjust view of the dashboards according to the difference between browser and server time.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/serverTime", method = RequestMethod.GET)
    @ResponseBody
    public long getServerTime() throws ThingsboardException {
        return System.currentTimeMillis();
    }

    @ApiOperation(value = "Get max data points limit (getMaxDatapointsLimit)",
            notes = "Get the maximum number of data points that dashboard may request from the server per in a single subscription command. " +
                    "This value impacts the time window behavior. It impacts 'Max values' parameter in case user selects 'None' as 'Data aggregation function'. " +
                    "It also impacts the 'Grouping interval' in case of any other 'Data aggregation function' is selected. " +
                    "The actual value of the limit is configurable in the system configuration file.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/maxDatapointsLimit", method = RequestMethod.GET)
    @ResponseBody
    public long getMaxDatapointsLimit() throws ThingsboardException {
        return maxDatapointsLimit;
    }

    @ApiOperation(value = "Get Dashboard Info (getDashboardInfoById)",
            notes = "Get the information about the dashboard based on 'dashboardId' parameter. " + DASHBOARD_INFO_DEFINITION,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/info/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public DashboardInfo getDashboardInfoById(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardInfoId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Dashboard (getDashboardById)",
            notes = "Get the dashboard based on 'dashboardId' parameter. " + DASHBOARD_DEFINITION,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public Dashboard getDashboardById(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Create Or Update Dashboard (saveDashboard)",
            notes = "Create or update the Dashboard. When creating dashboard, platform generates Dashboard Id as [time-based UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_1_(date-time_and_MAC_address)." +
                    "The newly created Dashboard id will be present in the response. " +
                    "Specify existing Dashboard id to update the dashboard. " +
                    "Referencing non-existing dashboard Id will cause 'Not Found' error. " +
                    "Only users with 'TENANT_ADMIN') authority may create the dashboards.",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard saveDashboard(
            @ApiParam(value = "A JSON value representing the dashboard.")
            @RequestBody Dashboard dashboard) throws ThingsboardException {
        try {
            dashboard.setTenantId(getCurrentUser().getTenantId());

            checkEntity(dashboard.getId(), dashboard, Resource.DASHBOARD);

            Dashboard savedDashboard = checkNotNull(dashboardService.saveDashboard(dashboard));

            logEntityAction(savedDashboard.getId(), savedDashboard,
                    null,
                    dashboard.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            if (dashboard.getId() != null) {
                sendEntityNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), EdgeEventActionType.UPDATED);
            }

            return savedDashboard;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.DASHBOARD), dashboard,
                    null, dashboard.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Delete the Dashboard (deleteDashboard)",
            notes = "Delete the Dashboard. Only users with 'TENANT_ADMIN') authority may delete the dashboards.")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteDashboard(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.DELETE);

            List<EdgeId> relatedEdgeIds = findRelatedEdgeIds(getTenantId(), dashboardId);

            dashboardService.deleteDashboard(getCurrentUser().getTenantId(), dashboardId);

            logEntityAction(dashboardId, dashboard,
                    null,
                    ActionType.DELETED, null, strDashboardId);

            sendDeleteNotificationMsg(getTenantId(), dashboardId, relatedEdgeIds);
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD),
                    null,
                    null,
                    ActionType.DELETED, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Assign the Dashboard (assignDashboardToCustomer)",
            notes = "Assign the Dashboard to specified Customer or do nothing if the Dashboard is already assigned to that Customer. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/dashboard/{dashboardId}", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard assignDashboardToCustomer(
            @ApiParam(value = CUSTOMER_ID_PARAM_DESCRIPTION)
            @PathVariable(CUSTOMER_ID) String strCustomerId,
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(CUSTOMER_ID, strCustomerId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);

            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);

            Dashboard savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));

            logEntityAction(dashboardId, savedDashboard,
                    customerId,
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, strCustomerId, customer.getName());

            sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.ASSIGNED_TO_CUSTOMER);

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId, strCustomerId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Unassign the Dashboard (unassignDashboardFromCustomer)",
            notes = "Unassign the Dashboard from specified Customer or do nothing if the Dashboard is already assigned to that Customer. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may unassign the dashboards from customers.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Dashboard unassignDashboardFromCustomer(
            @ApiParam(value = CUSTOMER_ID_PARAM_DESCRIPTION)
            @PathVariable(CUSTOMER_ID) String strCustomerId,
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);

            Dashboard savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));

            logEntityAction(dashboardId, dashboard,
                    customerId,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customer.getId().toString(), customer.getName());

            sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.UNASSIGNED_FROM_CUSTOMER);

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Update the Dashboard Customers (updateDashboardCustomers)",
            notes = "Updates the list of Customers that this Dashboard is assigned to. Removes previous assignments to customers that are not in the provided list. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard updateDashboardCustomers(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId,
            @ApiParam(value = "JSON array with the list of customer ids, or empty to remove all customers")
            @RequestBody(required = false) String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    customerIds.add(new CustomerId(toUUID(strCustomerId)));
                }
            }

            Set<CustomerId> addedCustomerIds = new HashSet<>();
            Set<CustomerId> removedCustomerIds = new HashSet<>();
            for (CustomerId customerId : customerIds) {
                if (!dashboard.isAssignedToCustomer(customerId)) {
                    addedCustomerIds.add(customerId);
                }
            }

            Set<ShortCustomerInfo> assignedCustomers = dashboard.getAssignedCustomers();
            if (assignedCustomers != null) {
                for (ShortCustomerInfo customerInfo : assignedCustomers) {
                    if (!customerIds.contains(customerInfo.getCustomerId())) {
                        removedCustomerIds.add(customerInfo.getCustomerId());
                    }
                }
            }

            if (addedCustomerIds.isEmpty() && removedCustomerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : addedCustomerIds) {
                    savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    ShortCustomerInfo customerInfo = savedDashboard.getAssignedCustomerInfo(customerId);
                    logEntityAction(dashboardId, savedDashboard,
                            customerId,
                            ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                    sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.ASSIGNED_TO_CUSTOMER);
                }
                for (CustomerId customerId : removedCustomerIds) {
                    ShortCustomerInfo customerInfo = dashboard.getAssignedCustomerInfo(customerId);
                    savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    logEntityAction(dashboardId, dashboard,
                            customerId,
                            ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                    sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.UNASSIGNED_FROM_CUSTOMER);
                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Adds the Dashboard Customers (addDashboardCustomers)",
            notes = "Adds the list of Customers to the existing list of assignments for the Dashboard. Keeps previous assignments to customers that are not in the provided list. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers/add", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard addDashboardCustomers(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId,
            @ApiParam(value = "JSON array with the list of customer ids")
            @RequestBody String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    CustomerId customerId = new CustomerId(toUUID(strCustomerId));
                    if (!dashboard.isAssignedToCustomer(customerId)) {
                        customerIds.add(customerId);
                    }
                }
            }

            if (customerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : customerIds) {
                    savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    ShortCustomerInfo customerInfo = savedDashboard.getAssignedCustomerInfo(customerId);
                    logEntityAction(dashboardId, savedDashboard,
                            customerId,
                            ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                    sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.ASSIGNED_TO_CUSTOMER);
                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Remove the Dashboard Customers (removeDashboardCustomers)",
            notes = "Removes the list of Customers from the existing list of assignments for the Dashboard. Keeps other assignments to customers that are not in the provided list. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/dashboard/{dashboardId}/customers/remove", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard removeDashboardCustomers(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId,
            @ApiParam(value = "JSON array with the list of customer ids")
            @RequestBody String[] strCustomerIds) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);

            Set<CustomerId> customerIds = new HashSet<>();
            if (strCustomerIds != null) {
                for (String strCustomerId : strCustomerIds) {
                    CustomerId customerId = new CustomerId(toUUID(strCustomerId));
                    if (dashboard.isAssignedToCustomer(customerId)) {
                        customerIds.add(customerId);
                    }
                }
            }

            if (customerIds.isEmpty()) {
                return dashboard;
            } else {
                Dashboard savedDashboard = null;
                for (CustomerId customerId : customerIds) {
                    ShortCustomerInfo customerInfo = dashboard.getAssignedCustomerInfo(customerId);
                    savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, customerId));
                    logEntityAction(dashboardId, dashboard,
                            customerId,
                            ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, customerId.toString(), customerInfo.getTitle());
                    sendEntityAssignToCustomerNotificationMsg(savedDashboard.getTenantId(), savedDashboard.getId(), customerId, EdgeEventActionType.UNASSIGNED_FROM_CUSTOMER);
                }
                return savedDashboard;
            }
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Assign the Dashboard to Public Customer (assignDashboardToPublicCustomer)",
            notes = "Assigns the dashboard to a special, auto-generated 'Public' Customer. Once assigned, unauthenticated users may browse the dashboard. " +
                    "This method is useful if you like to embed the dashboard on public web pages to be available for users that are not logged in. " +
                    "Be aware that making the dashboard public does not mean that it automatically makes all devices and assets you use in the dashboard to be public." +
                    "Use [assign Asset to Public Customer](#!/asset-controller/assignAssetToPublicCustomerUsingPOST) and " +
                    "[assign Device to Public Customer](#!/device-controller/assignDeviceToPublicCustomerUsingPOST) for this purpose. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/dashboard/{dashboardId}", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard assignDashboardToPublicCustomer(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.ASSIGN_TO_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(dashboard.getTenantId());
            Dashboard savedDashboard = checkNotNull(dashboardService.assignDashboardToCustomer(getCurrentUser().getTenantId(), dashboardId, publicCustomer.getId()));

            logEntityAction(dashboardId, savedDashboard,
                    publicCustomer.getId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strDashboardId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Unassign the Dashboard from Public Customer (unassignDashboardFromPublicCustomer)",
            notes = "Unassigns the dashboard from a special, auto-generated 'Public' Customer. Once unassigned, unauthenticated users may no longer browse the dashboard. " +
                    "Returns the Dashboard object. Only users with 'TENANT_ADMIN') authority may assign the dashboards to customers.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Dashboard unassignDashboardFromPublicCustomer(
            @ApiParam(value = DASHBOARD_ID_PARAM_DESCRIPTION)
            @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.UNASSIGN_FROM_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(dashboard.getTenantId());

            Dashboard savedDashboard = checkNotNull(dashboardService.unassignDashboardFromCustomer(getCurrentUser().getTenantId(), dashboardId, publicCustomer.getId()));

            logEntityAction(dashboardId, dashboard,
                    publicCustomer.getId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strDashboardId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strDashboardId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Tenant Dashboards by System Administrator (getTenantDashboards)",
            notes = "Returns a page of dashboard info objects owned by tenant. " + DASHBOARD_INFO_DEFINITION + " " + PAGE_DATA_PARAMETERS +
                    "Only users with 'SYS_ADMIN' authority may use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/tenant/{tenantId}/dashboards", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<DashboardInfo> getTenantDashboards(
            @ApiParam(value = TENANT_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(TENANT_ID) String strTenantId,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true)
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true)
            @RequestParam int page,
            @ApiParam(value = DASHBOARD_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION, allowableValues = DASHBOARD_SORT_PROPERTY_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            TenantId tenantId = new TenantId(toUUID(strTenantId));
            checkTenantId(tenantId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Tenant Dashboards (getTenantDashboards)",
            notes = "Returns a page of dashboard info objects owned by the tenant of a current user. " + DASHBOARD_INFO_DEFINITION + " " + PAGE_DATA_PARAMETERS +
                    "Only users with 'TENANT_ADMIN' authority may use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/dashboards", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<DashboardInfo> getTenantDashboards(
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true)
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true)
            @RequestParam int page,
            @ApiParam(value = HIDDEN_FOR_MOBILE)
            @RequestParam(required = false) Boolean mobile,
            @ApiParam(value = DASHBOARD_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION, allowableValues = DASHBOARD_SORT_PROPERTY_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (mobile != null && mobile) {
                return checkNotNull(dashboardService.findMobileDashboardsByTenantId(tenantId, pageLink));
            } else {
                return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Customer Dashboards (getCustomerDashboards)",
            notes = "Returns a page of dashboard info objects owned by the specified customer. " + DASHBOARD_INFO_DEFINITION + " " + PAGE_DATA_PARAMETERS +
                    "Only users with 'TENANT_ADMIN' or 'CUSTOMER_USER' authority may use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/dashboards", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<DashboardInfo> getCustomerDashboards(
            @ApiParam(value = CUSTOMER_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(CUSTOMER_ID) String strCustomerId,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true)
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true)
            @RequestParam int page,
            @ApiParam(value = HIDDEN_FOR_MOBILE)
            @RequestParam(required = false) Boolean mobile,
            @ApiParam(value = DASHBOARD_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION, allowableValues = DASHBOARD_SORT_PROPERTY_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        checkParameter(CUSTOMER_ID, strCustomerId);
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (mobile != null && mobile) {
                return checkNotNull(dashboardService.findMobileDashboardsByTenantIdAndCustomerId(tenantId, customerId, pageLink));
            } else {
                return checkNotNull(dashboardService.findDashboardsByTenantIdAndCustomerId(tenantId, customerId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Home Dashboard (getHomeDashboard)",
            notes = "Returns the home dashboard object that is configured as 'homeDashboardId' parameter in the 'additionalInfo' of the User. " +
                    "If 'homeDashboardId' parameter is not set on the User level and the User has authority 'CUSTOMER_USER', check the same parameter for the corresponding Customer. " +
                    "If 'homeDashboardId' parameter is not set on the User and Customer levels then checks the same parameter for the Tenant that owns the user. "
                    + DASHBOARD_DEFINITION + " " +
                    "Only users with 'TENANT_ADMIN' or 'CUSTOMER_USER' authority should use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/dashboard/home", method = RequestMethod.GET)
    @ResponseBody
    public HomeDashboard getHomeDashboard() throws ThingsboardException {
        try {
            SecurityUser securityUser = getCurrentUser();
            if (securityUser.isSystemAdmin()) {
                return null;
            }
            User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
            JsonNode additionalInfo = user.getAdditionalInfo();
            HomeDashboard homeDashboard;
            homeDashboard = extractHomeDashboardFromAdditionalInfo(additionalInfo);
            if (homeDashboard == null) {
                if (securityUser.isCustomerUser()) {
                    Customer customer = customerService.findCustomerById(securityUser.getTenantId(), securityUser.getCustomerId());
                    additionalInfo = customer.getAdditionalInfo();
                    homeDashboard = extractHomeDashboardFromAdditionalInfo(additionalInfo);
                }
                if (homeDashboard == null) {
                    Tenant tenant = tenantService.findTenantById(securityUser.getTenantId());
                    additionalInfo = tenant.getAdditionalInfo();
                    homeDashboard = extractHomeDashboardFromAdditionalInfo(additionalInfo);
                }
            }
            return homeDashboard;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Home Dashboard Info (getHomeDashboardInfo)",
            notes = "Returns the home dashboard info object that is configured as 'homeDashboardId' parameter in the 'additionalInfo' of the User. " +
                    "If 'homeDashboardId' parameter is not set on the User level and the User has authority 'CUSTOMER_USER', check the same parameter for the corresponding Customer. " +
                    "If 'homeDashboardId' parameter is not set on the User and Customer levels then checks the same parameter for the Tenant that owns the user. " +
                    "Only users with 'TENANT_ADMIN' or 'CUSTOMER_USER' authority should use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/dashboard/home/info", method = RequestMethod.GET)
    @ResponseBody
    public HomeDashboardInfo getHomeDashboardInfo() throws ThingsboardException {
        try {
            SecurityUser securityUser = getCurrentUser();
            if (securityUser.isSystemAdmin()) {
                return null;
            }
            User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
            JsonNode additionalInfo = user.getAdditionalInfo();
            HomeDashboardInfo homeDashboardInfo;
            homeDashboardInfo = extractHomeDashboardInfoFromAdditionalInfo(additionalInfo);
            if (homeDashboardInfo == null) {
                if (securityUser.isCustomerUser()) {
                    Customer customer = customerService.findCustomerById(securityUser.getTenantId(), securityUser.getCustomerId());
                    additionalInfo = customer.getAdditionalInfo();
                    homeDashboardInfo = extractHomeDashboardInfoFromAdditionalInfo(additionalInfo);
                }
                if (homeDashboardInfo == null) {
                    Tenant tenant = tenantService.findTenantById(securityUser.getTenantId());
                    additionalInfo = tenant.getAdditionalInfo();
                    homeDashboardInfo = extractHomeDashboardInfoFromAdditionalInfo(additionalInfo);
                }
            }
            return homeDashboardInfo;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Get Tenant Home Dashboard Info (getTenantHomeDashboardInfo)",
            notes = "Returns the home dashboard info object that is configured as 'homeDashboardId' parameter in the 'additionalInfo' of the corresponding tenant. " +
            "Only users with 'TENANT_ADMIN' authority may use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/dashboard/home/info", method = RequestMethod.GET)
    @ResponseBody
    public HomeDashboardInfo getTenantHomeDashboardInfo() throws ThingsboardException {
        try {
            Tenant tenant = tenantService.findTenantById(getTenantId());
            JsonNode additionalInfo = tenant.getAdditionalInfo();
            DashboardId dashboardId = null;
            boolean hideDashboardToolbar = true;
            if (additionalInfo != null && additionalInfo.has(HOME_DASHBOARD_ID) && !additionalInfo.get(HOME_DASHBOARD_ID).isNull()) {
                String strDashboardId = additionalInfo.get(HOME_DASHBOARD_ID).asText();
                dashboardId = new DashboardId(toUUID(strDashboardId));
                if (additionalInfo.has(HOME_DASHBOARD_HIDE_TOOLBAR)) {
                    hideDashboardToolbar = additionalInfo.get(HOME_DASHBOARD_HIDE_TOOLBAR).asBoolean();
                }
            }
            return new HomeDashboardInfo(dashboardId, hideDashboardToolbar);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @ApiOperation(value = "Update Tenant Home Dashboard Info (getTenantHomeDashboardInfo)",
            notes = "Update the home dashboard assignment for the current tenant. " +
                    "Only users with 'TENANT_ADMIN' authority may use this method.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/dashboard/home/info", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void setTenantHomeDashboardInfo(
            @ApiParam(value = "A JSON object that represents home dashboard id and other parameters", required = true)
            @RequestBody HomeDashboardInfo homeDashboardInfo) throws ThingsboardException {
        try {
            if (homeDashboardInfo.getDashboardId() != null) {
                checkDashboardId(homeDashboardInfo.getDashboardId(), Operation.READ);
            }
            Tenant tenant = tenantService.findTenantById(getTenantId());
            JsonNode additionalInfo = tenant.getAdditionalInfo();
            if (additionalInfo == null || !(additionalInfo instanceof ObjectNode)) {
                additionalInfo = JacksonUtil.OBJECT_MAPPER.createObjectNode();
            }
            if (homeDashboardInfo.getDashboardId() != null) {
                ((ObjectNode) additionalInfo).put(HOME_DASHBOARD_ID, homeDashboardInfo.getDashboardId().getId().toString());
                ((ObjectNode) additionalInfo).put(HOME_DASHBOARD_HIDE_TOOLBAR, homeDashboardInfo.isHideDashboardToolbar());
            } else {
                ((ObjectNode) additionalInfo).remove(HOME_DASHBOARD_ID);
                ((ObjectNode) additionalInfo).remove(HOME_DASHBOARD_HIDE_TOOLBAR);
            }
            tenant.setAdditionalInfo(additionalInfo);
            tenantService.saveTenant(tenant);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private HomeDashboardInfo extractHomeDashboardInfoFromAdditionalInfo(JsonNode additionalInfo) {
        try {
            if (additionalInfo != null && additionalInfo.has(HOME_DASHBOARD_ID) && !additionalInfo.get(HOME_DASHBOARD_ID).isNull()) {
                String strDashboardId = additionalInfo.get(HOME_DASHBOARD_ID).asText();
                DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
                checkDashboardId(dashboardId, Operation.READ);
                boolean hideDashboardToolbar = true;
                if (additionalInfo.has(HOME_DASHBOARD_HIDE_TOOLBAR)) {
                    hideDashboardToolbar = additionalInfo.get(HOME_DASHBOARD_HIDE_TOOLBAR).asBoolean();
                }
                return new HomeDashboardInfo(dashboardId, hideDashboardToolbar);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private HomeDashboard extractHomeDashboardFromAdditionalInfo(JsonNode additionalInfo) {
        try {
            if (additionalInfo != null && additionalInfo.has(HOME_DASHBOARD_ID) && !additionalInfo.get(HOME_DASHBOARD_ID).isNull()) {
                String strDashboardId = additionalInfo.get(HOME_DASHBOARD_ID).asText();
                DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
                Dashboard dashboard = checkDashboardId(dashboardId, Operation.READ);
                boolean hideDashboardToolbar = true;
                if (additionalInfo.has(HOME_DASHBOARD_HIDE_TOOLBAR)) {
                    hideDashboardToolbar = additionalInfo.get(HOME_DASHBOARD_HIDE_TOOLBAR).asBoolean();
                }
                return new HomeDashboard(dashboard, hideDashboardToolbar);
            }
        } catch (Exception e) {
        }
        return null;
    }

    @ApiOperation(value = "Assign dashboard to edge (assignDashboardToEdge)",
            notes = "Creates assignment of an existing dashboard to an instance of The Edge. " +
                    EDGE_ASSIGN_ASYNC_FIRST_STEP_DESCRIPTION +
                    "Second, remote edge service will receive a copy of assignment dashboard " +
                    EDGE_ASSIGN_RECEIVE_STEP_DESCRIPTION + ". " +
                    "Third, once dashboard will be delivered to edge service, it's going to be available for usage on remote edge instance.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/dashboard/{dashboardId}", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard assignDashboardToEdge(@PathVariable("edgeId") String strEdgeId,
                                           @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.READ);

            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            checkDashboardId(dashboardId, Operation.READ);

            Dashboard savedDashboard = checkNotNull(dashboardService.assignDashboardToEdge(getCurrentUser().getTenantId(), dashboardId, edgeId));

            logEntityAction(dashboardId, savedDashboard,
                    null,
                    ActionType.ASSIGNED_TO_EDGE, null, strDashboardId, strEdgeId, edge.getName());

            sendEntityAssignToEdgeNotificationMsg(getTenantId(), edgeId, savedDashboard.getId(), EdgeEventActionType.ASSIGNED_TO_EDGE);

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.ASSIGNED_TO_EDGE, e, strDashboardId, strEdgeId);

            throw handleException(e);
        }
    }

    @ApiOperation(value = "Unassign dashboard from edge (unassignDashboardFromEdge)",
            notes = "Clears assignment of the dashboard to the edge. " +
                    EDGE_UNASSIGN_ASYNC_FIRST_STEP_DESCRIPTION +
                    "Second, remote edge service will receive an 'unassign' command to remove dashboard " +
                    EDGE_UNASSIGN_RECEIVE_STEP_DESCRIPTION + ". " +
                    "Third, once 'unassign' command will be delivered to edge service, it's going to remove dashboard locally.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Dashboard unassignDashboardFromEdge(@PathVariable("edgeId") String strEdgeId,
                                               @PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.READ);
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.READ);

            Dashboard savedDashboard = checkNotNull(dashboardService.unassignDashboardFromEdge(getCurrentUser().getTenantId(), dashboardId, edgeId));

            logEntityAction(dashboardId, dashboard,
                    null,
                    ActionType.UNASSIGNED_FROM_EDGE, null, strDashboardId, strEdgeId, edge.getName());

            sendEntityAssignToEdgeNotificationMsg(getTenantId(), edgeId, savedDashboard.getId(), EdgeEventActionType.UNASSIGNED_FROM_EDGE);

            return savedDashboard;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.DASHBOARD), null,
                    null,
                    ActionType.UNASSIGNED_FROM_EDGE, e, strDashboardId, strEdgeId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/{edgeId}/dashboards", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<DashboardInfo> getEdgeDashboards(
            @PathVariable("edgeId") String strEdgeId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            checkEdgeId(edgeId, Operation.READ);
            TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
            PageData<DashboardInfo> nonFilteredResult = dashboardService.findDashboardsByTenantIdAndEdgeId(tenantId, edgeId, pageLink);
            List<DashboardInfo> filteredDashboards = nonFilteredResult.getData().stream().filter(dashboardInfo -> {
                try {
                    accessControlService.checkPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ, dashboardInfo.getId(), dashboardInfo);
                    return true;
                } catch (ThingsboardException e) {
                    return false;
                }
            }).collect(Collectors.toList());
            PageData<DashboardInfo> filteredResult = new PageData<>(filteredDashboards,
                    nonFilteredResult.getTotalPages(),
                    nonFilteredResult.getTotalElements(),
                    nonFilteredResult.hasNext());
            return checkNotNull(filteredResult);
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}