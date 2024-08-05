import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import testingApi from "../dashboard/pages/testing/api"
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

    useEffect(() => {
        const fetchTestingStatus = () => {
            const id = setInterval(() => {
                testingApi.fetchTestingRunStatus().then((resp) => {
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

        fetchTestingStatus();

        return () => {
            clearInterval(intervalIdRef.current);
        };
    }, []);

    const clearPollingInterval = () => {
        clearInterval(intervalIdRef.current);
    };

    return (
        <PollingContext.Provider value={{ currentTestsObj, clearPollingInterval }}>
            {children}
        </PollingContext.Provider>
    );
};
