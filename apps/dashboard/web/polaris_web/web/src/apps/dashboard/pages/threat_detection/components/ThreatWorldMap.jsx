import { useEffect, useState } from "react";
import Highcharts from "highcharts/highmaps";
import Exporting from "highcharts/modules/exporting";
import ExportData from "highcharts/modules/export-data";
import FullScreen from "highcharts/modules/full-screen";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { Spinner } from "@shopify/polaris";
import api from "../api";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

// Initialize modules
Exporting(Highcharts);
ExportData(Highcharts);
FullScreen(Highcharts);

function ThreatWorldMap({ startTimestamp, endTimestamp,  style}) {

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState(false);

    const fetchActorsPerCountry = async () => {
      // setLoading(true);
      const res = await api.getActorsCountPerCounty(startTimestamp, endTimestamp);
      if (res?.actorsCountPerCountry) {
        setData(
          res.actorsCountPerCountry.map((x) => {
            return {
              code: x.country,
              z: 100,
              count: x.count,
            };
          })
        );
      }
      setLoading(false);
    };

    const fetchMapData = async () => {
      const topology = await fetch(
        "https://code.highcharts.com/mapdata/custom/world.topo.json"
      ).then((response) => response.json());

      Highcharts.mapChart("threat-world-map-container", {
        chart: {
          map: topology,
          backgroundColor: "#fff",
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

        mapNavigation: {
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

        series: [
          {
            name: "Countries",
            color: "#E0E0E0",
            enableMouseTracking: false,
            states: {
              inactive: {
                enabled: true,
                opacity: 1,
              },
            },
          },
          {
            type: "mapbubble",
            name: "",
            data: data,
            minSize: "4%",
            maxSize: "4%",
            joinBy: ["iso-a2", "code"],
            marker: {
              fillOpacity: 0.5,
              lineWidth: 0,
            },
            tooltip: {
              pointFormat: "<b>{point.name}</b><br>Actors: {point.count}",
            },
          },
        ],
      });
    };

  useEffect(() => {
    if (data) {
      fetchMapData();
    }
  }, [data]);

  useEffect(() => {
    fetchActorsPerCountry();
    fetchMapData();
  }, [startTimestamp, endTimestamp]);

  if (loading) {
    return <Spinner />;
  }

  return (
    <InfoCard
      title={`${mapLabel("Threat", getDashboardCategory())} Actor Map`}
      titleToolTip={`${mapLabel("Threat", getDashboardCategory())} Actor Map`}
      component={<div id="threat-world-map-container" style={style}></div>}
    />
  );
}

export default ThreatWorldMap;
