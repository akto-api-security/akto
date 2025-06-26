import { Button, Popover } from "@shopify/polaris";
import { useState } from "react";
import { CalendarMinor } from "@shopify/polaris-icons"
import DateRangePicker from "./DateRangePicker";
import values from "@/util/values";
import func from "@/util/func"

function DateRangeFilter(props){

    const {dispatch, initialDispatch, disabled = false} = props;
    const [popoverActive, setPopoverActive] = useState(false);
    
    const handlePopoverState = (popoverState) =>{
        setPopoverActive(popoverState)
    }
    
    return (
        <Popover
        active={popoverActive}
        autofocusTarget="none"
        preferredAlignment="right"
        preferredPosition="below"
        fluidContent
        sectioned={false}
        fullHeight
        activator={
          <Button
            icon={CalendarMinor}
            onClick={() => setPopoverActive(!popoverActive)}
            disabled={disabled}
          >
            <span style={{whiteSpace: "nowrap"}}>
              {func.getDateValue(initialDispatch)}
            </span>
          </Button>
        }
        onClose={() => setPopoverActive(false)}
      >
        <DateRangePicker 
            ranges={values.ranges}
            setPopoverState={handlePopoverState} 
            dispatch={dispatch}
            initialDispatch={initialDispatch}
        />
        </Popover>
    )

}

export default DateRangeFilter