import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import testingApi from "../dashboard/pages/testing/api"
import homeRequests from '../dashboard/pages/home/api';
import PersistStore from './PersistStore';
import { isEndpointSecurityCategory } from './labelHelper';
const PollingContext = createContext();
const ACTIVE_POLL_MS = 2000;
const IDLE_POLL_MS = 10000;

export const usePolling = () => useContext(PollingContext);

export const PollingProvider = ({ children }) => {
    const [currentTestsObj, setCurrentTestsObj] = useState({
        totalTestsCompleted: 0,
        totalTestsInitiated: 0,
        totalTestsQueued: 0,
        testRunsArr: [],
    });
    const intervalIdRef = useRef(null);
    const intervalAlertRef = useRef(null);
    const [currentTestingRuns, setCurrentTestingRuns] = useState([])
    const pollingStoppedRef = useRef(false);

    useEffect(() => {
        // Poll fast only while tests are running or queued, and not at all while the tab is hidden
        let effectActive = true;
        const scheduleNextPoll = (delay) => {
            // A request still in flight at unmount must not start a new poll
            if (!effectActive) {
                return;
            }
            clearTimeout(intervalIdRef.current);
            intervalIdRef.current = setTimeout(pollTestingStatus, delay);
        };
        const onVisibilityChange = () => {
            if (!document.hidden && !pollingStoppedRef.current) {
                scheduleNextPoll(0);
            }
        };
        const pollTestingStatus = () => {
            if (pollingStoppedRef.current || document.hidden) {
                return;
            }
            testingApi.fetchTestingRunStatus().then((resp) => {
                setCurrentTestingRuns((prev) => {
                    if(prev.length === 0 && resp?.currentRunningTestsStatus === 0){
                        return prev
                    }else{
                        return resp?.currentRunningTestsStatus
                    }
                })
                setCurrentTestsObj(prevState => {
                    const newTestsObj = {
                        totalTestsInitiated: resp?.testRunsScheduled || 0,
                        totalTestsCompleted: resp?.totalTestsCompleted || 0,
                        totalTestsQueued: resp?.testRunsQueued || 0,
                        testRunsArr: resp?.currentRunningTestsStatus || []
                    };
                    if (JSON.stringify(prevState) !== JSON.stringify(newTestsObj)) {
                        return newTestsObj;
                    }
                    return prevState;
                });
                const testsActive = (resp?.currentRunningTestsStatus?.length || 0) > 0 || (resp?.testRunsQueued || 0) > 0;
                scheduleNextPoll(testsActive ? ACTIVE_POLL_MS : IDLE_POLL_MS);
            }).catch(() => {
                scheduleNextPoll(IDLE_POLL_MS);
            });
        };
        const fetchTestingStatus = () => {
            document.addEventListener('visibilitychange', onVisibilityChange);
            scheduleNextPoll(ACTIVE_POLL_MS);
        };
        const fetchAlerts = () => {
            const id2 = setInterval(() => {
                homeRequests.getTrafficAlerts().then((resp) => {
                    PersistStore.getState().setTrafficAlerts(resp)
                });
            }, (5000 * 60));
            intervalAlertRef.current = id2
        };
        if (window.location.pathname.startsWith('/dashboard')) {
            if (!isEndpointSecurityCategory()) {
                fetchTestingStatus();
            }
            if (window.USER_NAME.length > 0 && window.USER_NAME.includes('akto.io')) {
                fetchAlerts();
            }
        }
        return () => {
            effectActive = false;
            document.removeEventListener('visibilitychange', onVisibilityChange);
            clearTimeout(intervalIdRef.current);
            clearInterval(intervalAlertRef.current);
        };
    }, []);

    const clearPollingInterval = () => {
        pollingStoppedRef.current = true;
        clearTimeout(intervalIdRef.current);
        clearInterval(intervalAlertRef.current);
    };

    return (
        <PollingContext.Provider value={{ currentTestsObj, currentTestingRuns, clearPollingInterval }}>
            {children}
        </PollingContext.Provider>
    );
};
