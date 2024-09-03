import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import testingApi from "../dashboard/pages/testing/api"
import homeRequests from '../dashboard/pages/home/api';
import PersistStore from './PersistStore';
const PollingContext = createContext();

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

    useEffect(() => {
        const fetchTestingStatus = () => {
            const id = setInterval(() => {
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
                });
            }, 2000);
            intervalIdRef.current = id; 
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
            fetchTestingStatus();
            if (window.USER_NAME.length > 0 && window.USER_NAME.includes('akto.io')) {
                fetchAlerts();
            }
        }
        return () => {
            clearInterval(intervalIdRef.current);
            clearInterval(intervalAlertRef.current);
        };
    }, []);

    const clearPollingInterval = () => {
        clearInterval(intervalIdRef.current);
        clearInterval(intervalAlertRef.current);
    };

    return (
        <PollingContext.Provider value={{ currentTestsObj, currentTestingRuns, clearPollingInterval }}>
            {children}
        </PollingContext.Provider>
    );
};
