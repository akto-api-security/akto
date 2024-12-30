import { Button, Popover } from "@shopify/polaris";
import { useState } from "react";
import { CalendarIcon } from "@shopify/polaris-icons";
import DateRangePicker from "./DateRangePicker";
import values from "@/util/values";
import func from "@/util/func"

function DateRangeFilter(props){

    const {dispatch, initialDispatch} = props;
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
        <div className="polaris-secondaryAction-button">
        <Button
          icon={CalendarIcon}
          onClick={() => setPopoverActive(!popoverActive)}
        >
          <span style={{whiteSpace: "nowrap"}}>
            {func.getDateValue(initialDispatch)}
          </span>
        </Button>
        </div>
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
    );

}

export default DateRangeFilter