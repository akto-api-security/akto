import * as React from 'react';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit } from '@fortawesome/free-regular-svg-icons'

import TemplateStringEditor from './TemplateStringEditor.jsx';

import './start-node.css'

const RequestEditor = ({sampleApiCall}) => {
  return (
    <div>
      <div className="request-title">Query params</div>
      <div className="request-editor request-editor-path">
        <TemplateStringEditor defaultText={sampleApiCall.path.indexOf("?") > -1 ?sampleApiCall.path.split("?")[1] : "-"}/>
      </div>
      <div className="request-title">Headers</div>
      <div className="request-editor request-editor-headers">
        {<TemplateStringEditor defaultText={sampleApiCall.requestHeaders}/>}
      </div>
      <div className="request-title">Payload</div>
      <div className="request-editor request-editor-payload">
        <TemplateStringEditor defaultText={sampleApiCall.requestPayload}/>
      </div>
    </div>
  )
}

export default function InputArgumentsDialog({endpointDetails, fetchSampleDataFunc}) {
  const [open, setOpen] = React.useState(false);
  const [sampleData, updateSampleData] = React.useState({});
  React.useEffect(() => {
      const getSampleData = async () => {
        const json = await fetchSampleDataFunc(endpointDetails.endpoint, endpointDetails.apiCollectionId, endpointDetails.method)
        updateSampleData(json);
      }
      getSampleData();
    }, 
    [endpointDetails]
  );


  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <div>
      <div style={{float: "right"}}>
        <IconButton onClick={handleClickOpen}>
          <FontAwesomeIcon icon={faEdit} className="primary-btn" />
        </IconButton>
      </div>
      <Dialog open={open} onClose={handleClose} className="input-arguments-dialog">
        <div className="request-title"></div>
        <DialogContent>
            { 
              sampleData && 
              sampleData.sampleDataList && 
              sampleData.sampleDataList[0] && 
              sampleData.sampleDataList[0].samples && 
              sampleData.sampleDataList[0].samples[0] && 
              <RequestEditor sampleApiCall={JSON.parse(sampleData.sampleDataList[0].samples[0])}/>
            }
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} variant="contained" style={{textTransform: "unset"}}>Save</Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}