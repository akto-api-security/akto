import {
  CircleCancelMinor,
  CircleTickMinor,
  CalendarMinor,
  MagicMinor,
  FraudProtectMinor,
  PlayMinor,
  ClockMinor
} from '@shopify/polaris-icons';

export default {
    getSeverityStatus(countIssues){
        return Object.keys(countIssues).filter((key) => {
          return (countIssues[key]>0)
        })
      },
      getTestingRunIcon(state){
        switch(state){
            case "RUNNING": return ClockMinor;
            case "SCHEDULED": return CalendarMinor;
            case "STOPPED": return CircleCancelMinor;
            default: return ClockMinor;
        }
    },
    getSeverity(countIssues){
      console.log(countIssues)
      if(countIssues ==null ){
        return []
      }
      return Object.keys(countIssues).filter((key) => {
        return (countIssues[key]>0)
      }).map((key) => {
        return {
          confidence : key,
          count:countIssues[key]
        }
      })
    },
    getStatus(item) {
      let confidence = item.confidence.toUpperCase();
      switch (confidence) {
          case 'HIGH': return 'critical';
          case 'MEDIUM': return 'warning';
          case 'LOW': return 'neutral';
      }
  }
}