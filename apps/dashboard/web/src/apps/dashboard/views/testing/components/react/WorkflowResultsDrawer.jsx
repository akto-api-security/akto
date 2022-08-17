import React, { useState, useEffect, Component} from 'react'

import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import func from "@/util/func";
import TextFieldCloseable from './TextFieldCloseable.jsx'
import './start-node.css'


const WorkflowResultsDrawer = (props) => {

    let workflowTestResult = props["workflowTestResult"]
    let workflowTestingRun = props["workflowTestingRun"]

    const [idx, setIdx] = useState(0);

    const elemStyle = (i) =>  {
        return {
            marginY: 3, marginX: 1.5, background: "rgba(71,70,106,.03)", padding: 0, textAlign: "center", height: "30px", 
            lineHeight: "30px", borderRadius: 2, fontWeight: i === idx ? "bold" : "normal", cursor: "pointer"
        }
    };

    const elem = (element, i) => {
        return (
            <Box onClick={()=>{setIdx(i)}} key={i} sx={elemStyle(i)}>
                {element["key"]}
            </Box>
        )
    }

    const elemList = () => {
        if (!workflowTestResult) return []
        return workflowTestResult.map((x, i) => elem(x, i))
    }

    const testStatus = () => {
        if (!workflowTestingRun) return null
        let scheduleTimestamp = workflowTestingRun["scheduleTimestamp"]
        return func.prettifyEpoch(scheduleTimestamp)
    }

    const navBarContent = () => {
        return  (
            <Grid container spacing={2} style={{ height: "75px", lineHeight: "60px" }}>
                <Grid item xs={10} style={{textAlign: "center", fontWeight: "bold", fontSize: "20px"}} >
                    Test Results
                </Grid>
                <Grid item xs={2} style={{fontSize: "14px", fontStyle: "italic"}}>
                    {testStatus()}
                </Grid>
            </Grid>
        )
    }

    const mainContent = () => {
        if (!workflowTestResult) return (<Box></Box>)
        let data = JSON.parse(workflowTestResult[idx]["message"])
        let request = data["request"]
        let response = data["response"]
        return (
            <div style={{paddingTop: "12px"}}>
                <div className="request-title">[Request] URL</div>
                <div className="request-editor request-editor-path">
                    {<TextFieldCloseable text={request["url"]}/> }
                </div>
                <div className="request-title">[Request] Query params</div>
                <div className="request-editor request-editor-path">
                    {<TextFieldCloseable text={request["queryParams"]}/> }
                </div>
                <div className="request-title">[Request] Headers</div>
                <div className="request-editor request-editor-headers">
                    {<TextFieldCloseable text={request["headers"]}/> }
                </div>
                <div className="request-title">[Request] Payload</div>
                <div className="request-editor request-editor-payload">
                    {<TextFieldCloseable text={request["body"]}/> }
                </div>
                <div className="request-title">[Response] Headers</div>
                <div className="request-editor request-editor-payload">
                    {<TextFieldCloseable text={response["headers"]}/> }
                </div>
                <div className="request-title">[Response] Status code</div>
                <div className="request-editor request-editor-payload">
                    {<TextFieldCloseable text={response["statusCode"]}/> }
                </div>
                <div className="request-title">[Response] Payload</div>
                <div className="request-editor request-editor-payload">
                    {<TextFieldCloseable text={response["body"]}/> }
                </div>
            </div>
        )
    }

    return (
        <Grid container spacing={1}>
            <Grid item xs={12} style={{background: "rgba(239,239,239,.5)"}}>
                {navBarContent()}
            </Grid>
            <Grid item xs={1} style={{height: "100vh", paddingTop: 0}}>
                <Box sx={{ borderRight: 1, height: "100%", borderColor: "lightGrey"}}>
                    {elemList()}
                </Box>
            </Grid>
            <Grid item xs={11} style={{height: "100vh", padding: 0}}>
                <Box sx={{ height: "100%", background: "white", paddingX: "12px"}}>
                    {mainContent()}
                </Box>
            </Grid>
        </Grid>
    )
}

export default WorkflowResultsDrawer
