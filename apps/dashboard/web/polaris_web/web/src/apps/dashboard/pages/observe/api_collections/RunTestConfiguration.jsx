import React, { useReducer, useState } from 'react';
import { VerticalStack, HorizontalGrid, Checkbox, TextField, HorizontalStack } from '@shopify/polaris';
import Dropdown from "../../../components/layouts/Dropdown";
import SingleDate from "../../../components/layouts/SingleDate";
import func from "@/util/func"

const RunTestConfiguration = ({ testRun, setTestRun, runTypeOptions, hourlyTimes, testRunTimeOptions, testRolesArr, maxConcurrentRequestsOptions, slackIntegrated, generateLabelForSlackIntegration,getLabel, timeFieldsDisabled, teamsTestingWebhookIntegrated, generateLabelForTeamsIntegration, jiraProjectMap,activeFromTesting}) => {
    const reducer = (state, action) => {
        switch (action.type) {
          case "update":
            let scheduledEpoch = new Date(action.obj['selectedDate']).getTime() / 1000;
            let hourlyLabel = testRun.hourlyLabel;
            if(hourlyLabel !== "Now"){
                const val = hourlyTimes.filter((item) => item.label === hourlyLabel)[0].value;
                scheduledEpoch += parseInt(val) * 60 * 60;  
            }
            const timeNow = new Date().getTime() / 1000;
            if(Math.abs(timeNow - scheduledEpoch) < 86400){
                scheduledEpoch = func.timeNow()
            }
            setTestRun(prev => ({
                ...prev,
                startTimestamp: scheduledEpoch
            }));
            return {...state, [action.key]: action.obj['selectedDate'] }
          default:
            return state;
        }
    };
    const initialState = {data: new Date()};
    const startDayToday = func.getStartOfTodayEpoch()
    const [state, dispatch] = useReducer(reducer, initialState);


    const allProjects = Object.keys(jiraProjectMap||{}).map((key) => {
        return {label:key, value: key}
    })

    const allIssuesType = Array.isArray(jiraProjectMap?.[testRun?.autoTicketingDetails?.projectId])
        ? jiraProjectMap[testRun.autoTicketingDetails.projectId].map((ele) => ({
            label: ele.issueType,
            value: ele.issueType
        }))
        : [];
  

    const allSeverity = ["Critical", "High", "Medium", "Low"].map((ele) => {
        return {label: ele, value: ele.toUpperCase()}
    }
    )

    function toggleCreateTicketCheckbox() {
        const firstProject = allProjects[0]?.value || "";
        const firstIssueType =
            Array.isArray(jiraProjectMap?.[firstProject]) &&
            jiraProjectMap[firstProject]?.[0]?.issueType
                ? jiraProjectMap[firstProject][0].issueType
                : "";
    
        const checkPrevToggle = !testRun?.autoTicketingDetails?.shouldCreateTickets;
    
        if (checkPrevToggle) {
            setTestRun((prev) => ({
                ...prev,
                autoTicketingDetails: {
                    ...prev.autoTicketingDetails,
                    shouldCreateTickets: true,
                    projectId: firstProject,
                    severities: ["CRITICAL", "HIGH"],
                    issueType: firstIssueType,
                },
            }));
        } else {
            setTestRun((prev) => ({
                ...prev,
                autoTicketingDetails: {
                    ...prev.autoTicketingDetails,
                    shouldCreateTickets: false,
                    projectId: "",
                    severities: [],
                    issueType: "",
                },
            }));
        }
    }

    return (
        <VerticalStack gap={"4"}>
            <HorizontalGrid gap={"4"} columns={"3"}>
                <Dropdown
                    label="Run Type"
                    disabled={timeFieldsDisabled}
                    menuItems={runTypeOptions}
                    initial={testRun.runTypeLabel}
                    selected={(runType) => {
                        let recurringDaily = false;
                        let continuousTesting = false;
                        let recurringMonthly = false;
                        let recurringWeekly = false;

                        if (runType === 'Continuously') {
                            continuousTesting = true;
                        } else if (runType === 'Daily') {
                            recurringDaily = true;
                        } else if (runType === 'Weekly') {
                            recurringWeekly = true;
                        } else if (runType === 'Monthly') {
                            recurringMonthly = true;
                        } 
                        setTestRun(prev => ({
                            ...prev,
                            recurringDaily,
                            continuousTesting,
                            runTypeLabel: runType,
                            recurringWeekly,
                            recurringMonthly
                        }));
                    }} />
                <div style={{ width: "100%" }}>
                    <SingleDate 
                        dispatch={dispatch}
                        data={state.data}
                        dataKey="selectedDate"
                        preferredPosition="above"
                        disableDatesBefore={new Date(new Date().setDate(new Date().getDate() - 1))}
                        label="Select date"
                        allowRange={false}
                        readOnly={true}
                    />
                </div>
                <Dropdown
                    label="Select Time:"
                    disabled={testRun.continuousTesting === true || timeFieldsDisabled}
                    menuItems={hourlyTimes.filter((item) => {
                        if(func.isSameDateAsToday(state.data)){
                            return  item.label === "Now" || func.timeNow() <= (startDayToday + parseInt(item.value) * 60 * 60)
                        }else{
                            return item.label !== "Now"
                        }
                    })}
                    initial={testRun.hourlyLabel}
                    selected={(hour) => {
                        let scheduledEpoch = new Date().getTime() / 1000;
                        if (hour !== "Now"){
                            let initialTime = func.isSameDateAsToday(state.data) ? startDayToday : testRun.startTimestamp;
                            scheduledEpoch = initialTime + parseInt(hour) * 60 * 60;
                        }else{
                            scheduledEpoch = testRun.startTimestamp
                        }
                        const hourlyTime = getLabel(hourlyTimes, hour);
                        setTestRun(prev => ({
                            ...prev,
                            startTimestamp: scheduledEpoch,
                            hourlyLabel: hourlyTime ? hourlyTime.label : ""
                        }));
                    }} />
            </HorizontalGrid>
            <HorizontalGrid gap={"4"} columns={"3"}>
                <Dropdown
                    label="Test run time:"
                    menuItems={testRunTimeOptions}
                    initial={testRun.testRunTimeLabel}
                    selected={(timeInSeconds) => {
                        let testRunTime;
                        if (timeInSeconds === "Till complete") testRunTime = -1;
                        else testRunTime = timeInSeconds;

                        const testRunTimeOption = getLabel(testRunTimeOptions, timeInSeconds);

                        setTestRun(prev => ({
                            ...prev,
                            testRunTime: testRunTime,
                            testRunTimeLabel: testRunTimeOption.label
                        }));
                    }} />
                    <Dropdown
                        menuItems={testRolesArr}
                        label="Select Test Role"
                        initial={testRun.testRoleLabel}
                        selected={(requests) => {
                            let testRole;
                            if (!(requests === "No test role selected")) { testRole = requests; }
                            const testRoleOption = getLabel(testRolesArr, requests);

                            setTestRun(prev => ({
                                ...prev,
                                testRoleId: testRole,
                                testRoleLabel: testRoleOption.label
                            }));
                        }} />
                    <Dropdown
                        menuItems={maxConcurrentRequestsOptions}
                        label="Max Concurrent Requests"
                        initial={getLabel(maxConcurrentRequestsOptions, testRun.maxConcurrentRequests.toString()).label}
                        selected={(requests) => {
                            let maxConcurrentRequests;
                            if (requests === "Default") maxConcurrentRequests = -1;
                            else maxConcurrentRequests = requests;

                            const maxConcurrentRequestsOption = getLabel(maxConcurrentRequestsOptions, requests);

                            setTestRun(prev => ({
                                ...prev,
                                maxConcurrentRequests: maxConcurrentRequests,
                                maxConcurrentRequestsLabel: maxConcurrentRequestsOption.label
                            }));
                        }} />
            </HorizontalGrid>
            <Checkbox
                label={slackIntegrated ? "Send slack alert post test completion" : generateLabelForSlackIntegration()}
                checked={testRun.sendSlackAlert}
                onChange={() => setTestRun(prev => ({ ...prev, sendSlackAlert: !prev.sendSlackAlert }))}
                disabled={!slackIntegrated}
            />
            <Checkbox
                label={teamsTestingWebhookIntegrated ? "Send MS Teams alert post test completion" : generateLabelForTeamsIntegration()}
                checked={testRun.sendMsTeamsAlert}
                onChange={() => setTestRun(prev => ({ ...prev, sendMsTeamsAlert: !prev.sendMsTeamsAlert }))}
                disabled={!teamsTestingWebhookIntegrated}
            />
            <HorizontalStack gap={4}>
                <Checkbox
                    disabled={activeFromTesting}
                    label="Auto-create tickets"
                    checked={testRun.autoTicketingDetails.shouldCreateTickets}
                    onChange={() => { toggleCreateTicketCheckbox()}}
                />
                {testRun.autoTicketingDetails.shouldCreateTickets &&
                    <>
                        <Dropdown
                            menuItems={allProjects}
                            selected={(val) => {
                                setTestRun(prev => ({ ...prev, autoTicketingDetails: { ...prev.autoTicketingDetails, projectId: val } }))
                            }}
                            disabled={activeFromTesting || !testRun.autoTicketingDetails.shouldCreateTickets}
                            placeHolder={"Select Project"}
                            initial={testRun.autoTicketingDetails.projectId}
                        />
                        <Dropdown
                            disabled={activeFromTesting || !testRun.autoTicketingDetails.shouldCreateTickets}
                            menuItems={allIssuesType}
                            selected={(val) => { setTestRun(prev => ({ ...prev, autoTicketingDetails: { ...prev.autoTicketingDetails, issueType: val } })) }}
                            placeHolder={"Select Issue Type"}
                            initial={testRun.autoTicketingDetails.issueType}
                        />
                        <Dropdown
                            menuItems={allSeverity}
                            placeHolder={"Select Severity"}
                            selected={(val) => {setTestRun(prev => ({ ...prev, autoTicketingDetails: { ...prev.autoTicketingDetails, severities: val }})) }}                            
                            allowMultiple={true}
                            disabled={activeFromTesting || !testRun.autoTicketingDetails.shouldCreateTickets}
                            initial={testRun.autoTicketingDetails.severities}
                        />
                    </>}


            </HorizontalStack>
            <HorizontalGrid columns={2}>
                <Checkbox
                    label="Use different target for testing"
                    checked={testRun.hasOverriddenTestAppUrl}
                    onChange={() => {
                        setTestRun(prev => ({ ...prev, hasOverriddenTestAppUrl: !prev.hasOverriddenTestAppUrl, overriddenTestAppUrl: "" }))
                    }}
                />
                {testRun.hasOverriddenTestAppUrl &&
                    <div style={{ width: '400px' }}>
                        <TextField
                            placeholder="Override test app host"
                            value={testRun.overriddenTestAppUrl}
                            onChange={(overriddenTestAppUrl) => setTestRun(prev => ({ ...prev, overriddenTestAppUrl: overriddenTestAppUrl }))}
                        />
                    </div>
                }
            </HorizontalGrid>
        </VerticalStack>
    );
};

export default RunTestConfiguration;