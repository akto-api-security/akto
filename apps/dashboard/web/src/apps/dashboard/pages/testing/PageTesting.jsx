import { useEffect } from "react";
import { Outlet } from "react-router-dom";
import TestingStore from "./testingStore";
import api from "./api";

const PageTesting = () => {

    const setSubCategoryMap = TestingStore(state => state.setSubCategoryMap);
    const setSubCategoryFromSourceConfigMap = TestingStore(state => state.setSubCategoryFromSourceConfigMap);

    useEffect(()=>{
        async function fetchData(){
            api.fetchAllSubCategories().then((resp) => {
                let subCategoryMap = {}
                resp.subCategories.forEach((x) => {
                  subCategoryMap[x.name] = x
                })
                let subCategoryFromSourceConfigMap = {}
                resp.testSourceConfigs.forEach((x) => {
                  subCategoryFromSourceConfigMap[x.id] = x
                })
                setSubCategoryMap(subCategoryMap)
                setSubCategoryFromSourceConfigMap(subCategoryFromSourceConfigMap)
            })
        }
        fetchData();
    }, [])

    return (
        <Outlet/>
    )
}

export default PageTesting