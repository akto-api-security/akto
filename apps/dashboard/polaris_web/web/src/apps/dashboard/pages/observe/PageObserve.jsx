import { Outlet } from "react-router-dom";
import Store from "../../store";
import { useEffect } from "react";
import api from "./api";

const PageObserve = () => {

    const setDataTypeNames = Store(state => state.setDataTypeNames)

    useEffect(() => {
        async function fetchData(){
            await api.fetchDataTypeNames().then((res) => {
                setDataTypeNames(res.allDataTypes);
            })
        }
        fetchData();
    }, [])

    return (
        <Outlet/>
    )
}

export default PageObserve