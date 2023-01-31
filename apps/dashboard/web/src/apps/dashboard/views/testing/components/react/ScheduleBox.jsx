
import React, { useState, useEffect, Component} from 'react'
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import dayjs from 'dayjs';
import TextField from '@mui/material/TextField';
import Grid from '@mui/material/Grid';
import { Button } from '@mui/material';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import func from '@/util/func'



const ScheduleBox = (props) => {
    let testingRun = props.testingRun;
    let saveFn = props.saveFn
    let deleteFn = props.deleteFn

    const [recurring, setRecurring] = useState(false)
    const [startTimestamp, setStartTimestamp] = useState(dayjs())

    useEffect(() => {
        if (testingRun) {
            setRecurring(testingRun.periodInSeconds > 0)
            setStartTimestamp(dayjs.unix(testingRun.scheduleTimestamp))
        }
    }, [testingRun])


    const handleRunDailyChange = (event) => {
        setRecurring(event.target.checked)
    }

    const handleChange = (x) => {
        setStartTimestamp(x)
    }

    const save = () => {
        let hours = startTimestamp.$H
        let minutes = startTimestamp.$m
        let dayStart = +func.dayStart(+new Date());
        let actualTs = parseInt(dayStart/1000) + hours * 60 * 60 + minutes * 60

        saveFn(recurring, actualTs)
    }

    const deleteSchedule = () => {
        deleteFn()
    }

    const label = () => {
        return testingRun ? "Currently scheduled at" : "Schedule at"
    }

    const finalButton = () => {
        if (testingRun) {
            return <Button onClick={deleteSchedule} style={{color: "white"}} size="small">Delete</Button>
        } else {
            return <Button onClick={save} style={{color: "white"}} size="small">Save</Button>
        }
    }
    
    return (
        <Grid container spacing={1} style={{padding: "12px", width: "250px", paddingBottom: "0px"}}>
            <Grid item xs={12}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                    <TimePicker
                        label={label()}
                        value={startTimestamp}
                        onChange={handleChange}
                        renderInput={(params) => <TextField {...params} />}
                        disabled={testingRun ? true: false}
                    />
                </LocalizationProvider>
            </Grid>
            <Grid item xs={8}>
                <FormControlLabel 
                    control={<Checkbox checked={recurring} disabled={testingRun ? true: false} onChange={handleRunDailyChange}/>}
                    label="Run daily"
                    />
            </Grid>
            <Grid item xs={4} style={{paddingTop: "14px"}}>
                {finalButton()}
            </Grid>
        </Grid>

    )
}


export default ScheduleBox