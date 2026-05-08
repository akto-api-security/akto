import func from "@/util/func";
import observeFunc from "../observe/transform"
import { Badge, Button, Text , HorizontalStack, Box} from "@shopify/polaris";
import LocalStore from "../../../main/LocalStorageStore";
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";

const subCategoryMap = LocalStore.getState().subCategoryMap;

const transform = {

    // return array of objects .
    // each object is a subcategory with all timestamps value as [ts, count]
    formatTrendData: (respObj)=>{
        let subCategories = new Set() ;
        let subCategoriesTrend = {} ;

        Object.keys(respObj).forEach((key)=> {
            const epochArr = respObj[key];
            let ts = Number(key) * 86400 * 1000 ;

            let combinedObj = {}
            epochArr.forEach(ele => {
                const shortName = subCategoryMap[ele.subCategory]?.superCategory?.name || ele.subCategory;
                if(combinedObj && combinedObj.hasOwnProperty(shortName)){
                    combinedObj[shortName] = (ele.count + combinedObj[shortName])
                }else{
                    combinedObj[shortName] = ele.count;
                }
                subCategories.add(shortName);
            });

            Object.keys(combinedObj).forEach((category) =>{
                if(subCategoriesTrend.hasOwnProperty(category)){
                    subCategoriesTrend[category].data.push([ts,combinedObj[category]])
                }else{
                    subCategoriesTrend[category] = {
                        data: [[ts , combinedObj[category]]],
                        color: func.getColorForCharts(category),
                        name: category
                    }
                }   
            })
        })
        let issuesTrend = []
        Object.keys(subCategoriesTrend).forEach((key)=>{
            issuesTrend.push(subCategoriesTrend[key])
        })

        return {
            trend: issuesTrend,
            allSubCategories: Array.from(subCategories)
        }
    },

    getRiskScoreTrendOptions: (data, riskScoreRanges, navigate)=>{
        let dataArr = [] ;
        for(let c = 3 ; c < 6 ; c++){
            const val = data[c] ? data[c] : 0;
            dataArr.push(val)
        }

        const riskScoreRangesRev = [...riskScoreRanges].reverse();

        const riskScoreChartOptions = {
            chart: {
                type: 'column',
                height: '280px',
                spacing: [5, 0, 0, 0],
            },
            credits: {
                enabled: false,
            },
            title: {
                text: '',
                align: 'left',
                margin: 20
            },
            xAxis: {
                categories: ['0-3', '3-4', '4-5'],
                crosshair: true,
                accessibility: {
                    description: 'Risk score values'
                }
            },
            yAxis: {
                min: 0,
                title: {
                    text: 'Number of ' + mapLabel('URLs', getDashboardCategory())
                }
            },
            legend: {
                enabled: false
            },
            plotOptions: {
                column: {
                    point: {
                        events: {
                            click: function () {
                                const columnIndex = this.index;
                                const apiCollectionId = riskScoreRangesRev[columnIndex].apiCollectionId;
                                navigate(`/dashboard/observe/inventory/${apiCollectionId}`)
                            }
                        }
                    }
                }
            },
            series: {
                minPointLength: 0,
                pointWidth: 40,
                cursor: 'pointer',
                data: dataArr
            }
        }
        return riskScoreChartOptions;
    },

    getCountInfo: (collectionsArr, coverageObject) => {
        let urlsCount = 0;
        let coverageCount = 0;
        collectionsArr.forEach((x) => {
            if (x.hasOwnProperty('type') && x.type === 'API_GROUP') {
                return;
            }
            
            // Only count active collections
            if (!x.deactivated) {
                urlsCount += x.urlsCount;
                coverageCount += coverageObject[x.id] || 0;
            }
        });
        return {
            totalUrls: urlsCount,
            coverage: urlsCount === 0 ? "0%" : Math.ceil((coverageCount * 100) / urlsCount) + '%'
        }
    },

    getSensitiveCount: (sensitiveData) => {
        let count = 0 ;
        sensitiveData.forEach((x)=> {
            count += (x.sensitiveUrlsInRequest + x.sensitiveUrlsInResponse)
        })
        return count ;
    },

    lightenColor: (hex, factor) => {
        // Convert hex to RGB
        hex = hex.replace(/^#/, '');
        const bigint = parseInt(hex, 16);
        const r = (bigint >> 16) & 255;
        const g = (bigint >> 8) & 255;
        const b = bigint & 255;
    
        // Calculate lighter RGB values
        const newR = Math.min(255, r + factor * (255 - r));
        const newG = Math.min(255, g + factor * (255 - g));
        const newB = Math.min(255, b + factor * (255 - b));
    
        // Convert back to hex
        const lighterHex = `#${Math.round(newR).toString(16).padStart(2, '0')}${Math.round(newG).toString(16).padStart(2, '0')}${Math.round(newB).toString(16).padStart(2, '0')}`;
        
        return lighterHex;
    },

    generateColors: (baseColor, numColors, lightenFactor) => {
        const colors = [baseColor];
        for (let i = 1; i < numColors; i++) {
            baseColor = transform.lightenColor(baseColor, lightenFactor);
            colors.push(baseColor);
        }
        return colors;
    },

    getTrendData: (obj)=>{
        const baseColor = '#6200EA';
        const lightenFactor = 0.2;
        if(Object.keys(obj).length === 0){
            return ;
        }

        let finalObj = {}
        const colorsArr = transform.generateColors(baseColor, Object.keys(obj).length , lightenFactor);

        Object.keys(obj).forEach((x,index)=> {
            finalObj[x] = {
                text: obj[x],
                color: colorsArr[index]
            }
        })

        const sortedEntries = Object.entries(finalObj).sort(([, val1], [, val2]) => {
            const prop1 = val1['text'];
            const prop2 = val2['text'];
            return prop2 - prop1 ;
        });

        return Object.fromEntries(sortedEntries)
    },

    getFormattedSensitiveData: (sensitiveData) =>{
        const requestObj = sensitiveData?.subTypeCountMap?.REQUEST || {}
        const responseObj = sensitiveData?.subTypeCountMap?.RESPONSE || {}
        return {
            request: transform.getTrendData(requestObj),
            response: transform.getTrendData(responseObj),
        }

    },

    getStatus: (coverage)=>{
        if(coverage <= 20){
            return "critical";
        }else if(coverage <= 50){
            return "highlight";
        }else{
            return "success";
        }
    },

    formatCoverageData: (coverageObj, collections) =>{
        const finalArr = [] ;
        collections.forEach((x) => {
            let coverage = coverageObj[x.id] ? Math.ceil((100 * coverageObj[x.id])/x.urlsCount) : 0;
            finalArr.push( {
                id: x.id,
                coverage: coverage,
                status: transform.getStatus(coverage)
            })
        })
        return finalArr.sort((a, b) => a.coverage - b.coverage);

    },

    prepareTableData: (riskScoreObj, collections, collectionsMap,setActive) => {
        let finalArr = [] ;
        collections.forEach((c) => {
            let score = riskScoreObj[c.id] || 0 
            let obj = {
                id: c.id,
                score: score,
                status: observeFunc.getStatus(score)
            }
            finalArr.push(obj)
        })

        finalArr.sort((a,b) => b.score - a.score) ;
        let tableRows = []
        finalArr.forEach((c)=> {
            let tempRow = [
                <Button onClick={()=>setActive(true)} plain monochrome removeUnderline  >

                  <Box width='250px'  > 
                 <HorizontalStack align="space-between" gap="2">
                <Text>{collectionsMap[c.id]}</Text>  

              
        
                <Badge status={c.status} size="small">{c.score.toString()}</Badge>
                </HorizontalStack>   
                </Box>
                </Button>
            
             
               
            ]
            tableRows.push(tempRow)
        })
        return tableRows
    },

    getConnectedSteps: (stepsObj) => {
        let count = 0 ;
        const oneMonth = func.recencyPeriod / 2
        Object.keys(stepsObj).forEach((x)=>{
            if(stepsObj[x].integrated || ((func.timeNow() - stepsObj[x].lastSkipped) <= oneMonth)){
                count++;
            }
        })
        return count
    }
}
export default transform;