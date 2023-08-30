import { useEffect } from "react";
import { Outlet } from "react-router-dom";
import transform from "./transform";

const PageTesting = () => {

    useEffect(()=>{
        async function fetchData(){
            transform.setTestMetadata();
        }
        fetchData();
    }, [])

    return (
        <Outlet/>
    )
}

export default PageTesting