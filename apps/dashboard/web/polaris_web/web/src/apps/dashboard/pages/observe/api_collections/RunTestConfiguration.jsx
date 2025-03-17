import React, { useReducer } from 'react';
import { VerticalStack, HorizontalGrid, Checkbox, TextField } from '@shopify/polaris';
import Dropdown from "../../../components/layouts/Dropdown";
import SingleDate from "../../../components/layouts/SingleDate";
import func from "@/util/func"

const RunTestConfiguration = ({ testRun, setTestRun, runTypeOptions, hourlyTimes, testRunTimeOptions, testRolesArr, maxConcurrentRequestsOptions, slackIntegrated, generateLabelForSlackIntegration,getLabel, timeFieldsDisabled, teamsTestingWebhookIntegrated, generateLabelForTeamsIntegration}) => {

    const reducer = (state, action) => {
        switch (action.type) {
          case "update":
            let scheduledEpoch = new Date(action.obj['selectedDate']).getTime() / 1000;
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
    const [state, dispatch] = useReducer(reducer, initialState);

    return (
        <VerticalStack gap={"4"}>
            <HorizontalGrid gap={"4"} columns={"3"}>
                <Dropdown
                    label="Run Type"
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
                        else if (runType === 'Once') {
                            // Always ensure we have a date when switching to Once
                            dispatch({
                                type: "update",
                                key: "data",
                                obj: { selectedDate: state.data || new Date() }
                            });
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
                {testRun.runTypeLabel === "Once" && (
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
                )}
                <Dropdown
                    label="Select Time:"
                    disabled={testRun.continuousTesting === true || timeFieldsDisabled}
                    menuItems={hourlyTimes}
                    initial={testRun.hourlyLabel}
                    selected={(hour) => {
                        let scheduledEpoch = new Date(state.data).getTime() / 1000;
                        if (hour !== "Now"){
                            scheduledEpoch += parseInt(hour) * 60 * 60;
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