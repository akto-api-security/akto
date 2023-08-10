import { Button, Popover } from "@shopify/polaris";
import { useEffect, useState } from "react";
import { CalendarMinor } from "@shopify/polaris-icons"
import DateRangePicker from "./DateRangePicker";
import values from "../../../../util/values";
import func from "@/util/func"

function DateRangeFilter(props){

    const {initialDate, getDate} = props;

    const [popoverActive, setPopoverActive] = useState(false);
    const [buttonValue, setButtonValue] = useState(values.ranges[0].title);

    useEffect(()=>{
      if(initialDate){
        const buttonValue = func.prettifyEpoch(initialDate.since) + " - " + func.prettifyEpoch(initialDate.until)
        setButtonValue(buttonValue)
      }
    }, [])

    const handleDate = (dateRange) => {
      const buttonValue =
        dateRange.title === "Custom"
          ? dateRange.period.since.toDateString() +
          " - " +
          dateRange.period.until.toDateString()
          : dateRange.title;
      setButtonValue(buttonValue)
      getDate(dateRange)
    }
    
    const handlePopoverState = (popoverState) =>{
        setPopoverActive(popoverState)
    }
    
    return (
        <Popover
        active={popoverActive}
        autofocusTarget="none"
        preferredAlignment="left"
        preferredPosition="below"
        fluidContent
        sectioned={false}
        fullHeight
        activator={
          <Button
            icon={CalendarMinor}
            onClick={() => setPopoverActive(!popoverActive)}
          >
            {buttonValue}
          </Button>
        }
        onClose={() => setPopoverActive(false)}
      >
        <DateRangePicker 
            ranges={values.ranges}
            getDate={handleDate} 
            setPopoverState={handlePopoverState} 
        />
        </Popover>
    )

}

export default DateRangeFilter