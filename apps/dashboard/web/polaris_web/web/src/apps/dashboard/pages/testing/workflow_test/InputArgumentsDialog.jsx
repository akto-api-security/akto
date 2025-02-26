import * as React from 'react';
import IconButton from '@mui/material/IconButton';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit, faCaretSquareDown } from '@fortawesome/free-regular-svg-icons'

import TemplateStringEditor from './TemplateStringEditor.jsx';
import useStore from './store'

import FormControlLabel from '@mui/material/FormControlLabel';

import Menu from '@mui/material/Menu';
import FormGroup from '@mui/material/FormGroup';
import Checkbox from '@mui/material/Checkbox';

import './start-node.css'
import { Divider } from '@mui/material';

const RequestEditor = ({sampleApiCall, updatedSampleData, onChangeApiRequest, testValidatorCode, onChangeTestValidatorCode}) => {
  let QUERY_PARAMS = "queryParams"
  let REQUEST_HEADERS = "requestHeaders"
  let REQUEST_PAYLOAD = "requestPayload"
  let REQUEST_URL = "requestUrl"


  let oldParams = sampleApiCall.path.indexOf("?") > -1 ?sampleApiCall.path.split("?")[1] : "";
  let oldHeaders = sampleApiCall.requestHeaders
  let oldPayload = sampleApiCall.requestPayload
  let oldUrl = sampleApiCall.path.split("?")[0]

  const onChangeQueryParams = (newParams) => {
    onChangeApiRequest(QUERY_PARAMS, newParams);
  }

  const onChangeRequestHeaders = (newHeaders) => {
    onChangeApiRequest(REQUEST_HEADERS, newHeaders);
  }

  const onChangeRequestPayload = (newPayload) => {
    onChangeApiRequest(REQUEST_PAYLOAD, newPayload);
  }

  const onChangeRequestUrl = (newUrl) => {
    onChangeApiRequest(REQUEST_URL, newUrl);
  }

  const onChangeTestValidator = (newCondition) => {
    onChangeApiRequest(TEST_VALIDATOR, newCondition);
  }

  return (
    <div>
      <div style={{display: "flex"}}>
        <div style={{width: "400px"}}>
          <div className="request-title">[Request] URL</div>
          <div className="request-editor request-editor-path">
            <TemplateStringEditor defaultText={updatedSampleData[REQUEST_URL] != null ? updatedSampleData[REQUEST_URL] : oldUrl} onChange={onChangeRequestUrl}/>
          </div>
          <div className="request-title">[Request] Query params</div>
          <div className="request-editor request-editor-path">
            <TemplateStringEditor defaultText={updatedSampleData[QUERY_PARAMS] != null ?  updatedSampleData[QUERY_PARAMS] : oldParams} onChange={onChangeQueryParams}/>
          </div>
          <div className="request-title">[Request] Headers</div>
          <div className="request-editor request-editor-headers">
            {<TemplateStringEditor defaultText={updatedSampleData[REQUEST_HEADERS] != null ? updatedSampleData[REQUEST_HEADERS] : oldHeaders} onChange={onChangeRequestHeaders}/>}
          </div>
          <div className="request-title">[Request] Payload</div>
          <div className="request-editor request-editor-payload">
            <TemplateStringEditor defaultText={updatedSampleData[REQUEST_PAYLOAD] != null ? updatedSampleData[REQUEST_PAYLOAD] : oldPayload} onChange={onChangeRequestPayload}/>
          </div>
        </div>
        <div style={{width: "400px", opacity: "0.5"}}>
          <div className="request-title">[Response] Headers</div>
          <div className="request-editor request-editor-headers">
            {sampleApiCall.responseHeaders}
          </div>
          <div className="request-title">[Response] Payload</div>
          <div className="request-editor request-editor-payload">
            {sampleApiCall.responsePayload}
          </div>
        </div>
      </div>
      <div style={{minHeight: "24px"}}></div>
    </div>
  )
}

export default function InputArgumentsDialog({nodeId, endpointDetails}) {

  const [open, setOpen] = React.useState(false);

  const addNodeEndpoint = useStore(state => state.addNodeEndpoint)
  const nodeEndpointMap = useStore(state => state.nodeEndpointMap)
  const setApiType = useStore(state => state.setApiType)
  const setRedirect = useStore(state => state.setRedirect)
  const [sampleData, updateSampleData] = React.useState({})
  const setValidatorCode = useStore(state => state.setValidatorCode)
  const setSleep = useStore(state => state.setSleep)

  React.useEffect(() => {
      if (!endpointDetails) return;
      let updatedSampleData = endpointDetails["updatedSampleData"]
      if (!updatedSampleData) return;
      updateSampleData(JSON.parse(updatedSampleData["orig"]) || {})
  }, [endpointDetails])

  const onChangeApiRequest = (key, newData) => {
    let currNewSampleData = {orig: JSON.stringify(sampleData)}
    currNewSampleData[key] = newData
    let updatedSampleData = {...nodeEndpointMap[nodeId].updatedSampleData, ...currNewSampleData}
    addNodeEndpoint(nodeId, {...endpointDetails, updatedSampleData})
  }

  const onChangeTestValidatorCode = (val) => {
    setValidatorCode(nodeId, val)
  }

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const handleChangePoll = (event) => {
    let isPoll = event.target.checked
    let type = isPoll ? "POLL" : "API"
    setApiType(nodeId, type)
  };

  const checkedPoll = () => {
    return endpointDetails["type"] === "POLL"
  }

  const handleChangeRedirect = (event) => {
    let redirect = event.target.checked
    setRedirect(nodeId, !redirect)
  };

  const handleSleep = (event) => {
    let waitInSeconds = event.target.checked ? 60 : 0
    setSleep(nodeId, waitInSeconds)
  };

  const checkedRedirect = () => {
    return !endpointDetails["overrideRedirect"]
  }

  const checkedSleep = () => {
    return endpointDetails["waitInSeconds"] > 0
  }

  const [anchorEl, setAnchorEl] = React.useState(null);
  const openSettings = Boolean(anchorEl);
  const handleSettingsClick = (event) => {
    setAnchorEl(event.currentTarget);
  };
  const handleSettingsClose = () => {
    setAnchorEl(null);
  };


  return (
    <div>
      <div style={{float: "right"}}>
        <IconButton onClick={handleClickOpen}>
          <FontAwesomeIcon icon={faEdit} className="primary-btn" />
        </IconButton>
      </div>
      <Dialog open={open} onClose={handleClose} className="input-arguments-dialog" style={{minWidth: "850px"}}>
        <div className="request-title"></div>
        <DialogContent>
            
          <div>
            <div style={{position: "absolute", right: 24,  top: 12}}>
              <IconButton
                aria-label="more"
                id="long-button"
                aria-controls={openSettings ? 'long-menu' : undefined}
                aria-expanded={openSettings ? 'true' : undefined}
                aria-haspopup="true"
                onClick={handleSettingsClick}
              >
                <FontAwesomeIcon icon={faCaretSquareDown}  size="sm"/>
              </IconButton>
              <Menu
                id="basic-menu"
                anchorEl={anchorEl}
                open={openSettings}
                onClose={handleSettingsClose}
                MenuListProps={{
                  'aria-labelledby': 'basic-button',
                }}
              >
                <FormGroup sx={{paddingLeft: 2}}>
                  <FormControlLabel control={<Checkbox checked={checkedSleep()} onChange={handleSleep}/>} label="Sleep" />
                  <FormControlLabel control={<Checkbox checked={checkedRedirect()} onChange={handleChangeRedirect}/>} label="Auto Redirect" />
                </FormGroup>
              </Menu>
            </div>
            <RequestEditor
              sampleApiCall={sampleData}
              updatedSampleData={nodeEndpointMap[nodeId].updatedSampleData}
              onChangeApiRequest={onChangeApiRequest}
              testValidatorCode={nodeEndpointMap[nodeId].testValidatorCode}
              onChangeTestValidatorCode={onChangeTestValidatorCode}
            />
          </div>
            
        </DialogContent>
      </Dialog>
    </div>
  );
}