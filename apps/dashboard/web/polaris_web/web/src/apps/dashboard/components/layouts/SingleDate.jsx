import { Box, Card, DatePicker, Popover, TextField, VerticalStack, Icon } from "@shopify/polaris";
import React, { useState, useEffect } from "react";
import { CalendarMinor } from "@shopify/polaris-icons";

function SingleDate(props) {

  const {dispatch, data, dataKey, index } = props;
  const [popoverActive, setPopoverActive] = useState(false);

    const [{ month, year }, setDate] = useState({
      month: new Date().getMonth(),
      year: new Date().getFullYear(),
    });
    const [inputValue, setInputValue] = useState("");
  
    const VALID_YYYY_MM_DD_DATE_REGEX = /^\d{4}-\d{1,2}-\d{1,2}/;

    function isDate(date) {
      return !isNaN(new Date(date).getDate());
    }
    function isValidYearMonthDayDateString(date) {
      return VALID_YYYY_MM_DD_DATE_REGEX.test(date) && isDate(date);
    }
    function isValidDate(date) {
      return date.length === 10 && isValidYearMonthDayDateString(date);
    }

    function parseYearMonthDayDateString(input) {
      const [year, month, day] = input.split("-");
      return new Date(Number(year), Number(month) - 1 , Number(day));
    }

    function handleInputValueChange(value) {
      setInputValue(value);
      if (isValidDate(value)) {
        const newSelectedDate = parseYearMonthDayDateString(value);
        dispatch({ type: "update", index: index, key: "data", obj: { [dataKey] : newSelectedDate } })
        setPopoverActive(false);
      }
    }
    function handleOnClose() {
      setPopoverActive(false);
    }
    function handleMonthChange(month, year) {
      setDate({ month, year });
    }
    function handleDateSelection({ end: newSelectedDate }) {
      dispatch({ type: "update", index: index, key: "data", obj: { [dataKey] : newSelectedDate } })
      setPopoverActive(false);
    }
    function formatDateToYearMonthDayDateString(date) {
      const year = String(date.getFullYear());
      let month = String(date.getMonth() + 1);
      let day = String(date.getDate());
      if (month.length < 2) {
        month = String(month).padStart(2, "0");
      }
      if (day.length < 2) {
        day = String(day).padStart(2, "0");
      }
      return [year, month, day].join("-");
    }
    function formatDate(date) {
      return formatDateToYearMonthDayDateString(date);
    }

    useEffect(() => {
      if (data) {
        setDate({
          month: data.getMonth(),
          year: data.getFullYear(),
        });
        setInputValue(formatDate(data));
      } else {
        setInputValue(dataKey);
      }
    }, [data]);
    return (
      <VerticalStack inlineAlign="center" gap="400">
        <Box minWidth="100%" padding={{ xs: 200 }}>
          <Popover
            active={popoverActive}
            autofocusTarget="none"
            preferredAlignment="left"
            fullWidth
            preferInputActivator={false}
            preferredPosition="below"
            onClose={handleOnClose}
            activator={
              <TextField
                role="combobox"
                prefix={<Icon source={CalendarMinor} />}
                value={inputValue}
                onFocus={() => setPopoverActive(true)}
                onChange={props?.readOnly ? () => {} : handleInputValueChange}
                autoComplete="off"
                label={props?.label}
              />
            }
          >
            <Card>
              <DatePicker
                month={month}
                year={year}
                selected={data}
                onMonthChange={handleMonthChange}
                onChange={handleDateSelection}
                disableDatesBefore={props?.disableDatesBefore}
              />
            </Card>
          </Popover>
        </Box>
      </VerticalStack>
    )
  }

  export default SingleDate