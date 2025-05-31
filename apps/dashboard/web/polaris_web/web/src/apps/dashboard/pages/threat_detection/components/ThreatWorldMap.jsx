import { useEffect } from "react";
import Highcharts from "highcharts/highmaps";
import Exporting from "highcharts/modules/exporting";
import ExportData from "highcharts/modules/export-data";
import FullScreen from "highcharts/modules/full-screen";
import InfoCard from "../../dashboard/new_components/InfoCard";
// Initialize modules
Exporting(Highcharts);
ExportData(Highcharts);
FullScreen(Highcharts);

function ThreatWorldMap({style, loading, onCountryClick, data }) {
  useEffect(() => {
    const fetchMapData = async () => {
      const topology = await fetch(
        "https://code.highcharts.com/mapdata/custom/world.topo.json"
      ).then((response) => response.json());

      Highcharts.mapChart("threat-world-map-container", {
        chart: {
          map: topology,
          backgroundColor: '#ffffff',
          style: {
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial'
          }
        },

        title: {
          text: undefined,
        },

        credits: {
          enabled: false,
        },

        subtitle: {
          text: "",
        },

        legend: {
          enabled: false,
        },

        mapView: {
          fitToGeometry: {
            type: "MultiPoint",
            coordinates: [
              [-164, 54], // Alaska west
              [-35, 84], // Greenland north
              [179, -38], // New Zealand east
              [-68, -55], // Chile south
            ],
          },
        },

       exporting: {
          enabled: true, // Enables export menu
          buttons: {
            contextButton: {
              menuItems: [
                "viewFullscreen",
                "separator",
                "downloadPNG",
                "downloadJPEG",
                "downloadPDF",
                "downloadSVG",
                "separator",
                "downloadCSV",
                "downloadXLS"
              ],
            },
          },
        },

        plotOptions: {
          map: {
            color: "#6200EA",
          },
          mapbubble: {
            animation: {
              duration: 450
            },
            minSize: 15,
            maxSize: 30,
          }
        },

        tooltip: {
          hideDelay: 0,
          animation: false,
          followPointer: true
        },

        series: [
          {
            type: "map",
            name: "Countries",
            enableMouseTracking: true,
            showInLegend: false,
          },
          {
            cursor: "pointer",
            events: {
              click: (e) => {
                onCountryClick(e.point.code);
              }
            },
            type: "mapbubble",
            name: "Threat Actors",
            enableMouseTracking: true,
            animation: {
              duration: 450,
            },
            stickyTracking: true,
            data: data.map(item => ({
              z: item.count,
              value: item.count,
              code: item.code,
              name: item.code,
              count: item.count
            })),
            joinBy: ["iso-a2", "code"],
            marker: {
              fillColor: {
                radialGradient: {
                  cx: 0.5,
                  cy: 0.5,
                  r: 0.7
                },
                stops: [
                  [0, 'rgba(255, 30, 0, 0.1)'],
                  [0.3, 'rgba(255, 30, 0, 0.2)'],
                  [0.6, 'rgba(255, 30, 0, 0.4)'],
                  [0.8, 'rgba(255, 30, 0, 0.7)'],
                  [1, 'rgba(255, 0, 0, 0.9)']
                ]
              },
              lineWidth: 0,
              symbol: 'circle'
            },
            dataLabels: {
              enabled: true,
              format: '{point.count}',
              align: 'center',
              verticalAlign: 'middle',
              style: {
                color: 'white',
                textOutline: '1px contrast',
                fontWeight: 'normal',
                fontSize: '12px'
              },
              allowOverlap: true,
              crop: false,
              overflow: 'none'
            },
            tooltip: {
              headerFormat: '',
              pointFormat: '<b>{point.name}</b><br>Threat Actors: <b>{point.count}</b>'
            },
          }
        ],
      });
    };

    fetchMapData();
  }, [data]);

  return <InfoCard
    title={"Threat Actor Map"}
    titleToolTip={"Threat Actor Map"}
    component={<div id="threat-world-map-container" style={style}></div>}
  />;
}

export default ThreatWorldMap;
