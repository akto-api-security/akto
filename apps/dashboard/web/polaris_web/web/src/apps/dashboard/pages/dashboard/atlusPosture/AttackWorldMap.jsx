import { useEffect, useCallback } from "react";
import Highcharts from "highcharts/highmaps";
import Exporting from "highcharts/modules/exporting";
import ExportData from "highcharts/modules/export-data";
import FullScreen from "highcharts/modules/full-screen";
import InfoCard from "../new_components/InfoCard";

// Initialize modules
Exporting(Highcharts);
ExportData(Highcharts);
FullScreen(Highcharts);

// Add CSS for animated lines
const style = document.createElement('style');
style.textContent = `
    @keyframes attack-flow-animation {
        to {
            stroke-dashoffset: -40;
        }
    }

    .animated-attack-line {
        stroke-dasharray: 10 10;
        animation: attack-flow-animation 1s linear infinite;
    }
`;
if (!document.head.querySelector('style[data-attack-map]')) {
    style.setAttribute('data-attack-map', 'true');
    document.head.appendChild(style);
}

function AttackWorldMap({ attackRequests, style }) {

    // Function to get color based on attack type
    const getColorForAttackType = useCallback((attackType) => {
        const colorMap = {
            "SQL_INJECTION": "#dc2626",
            "SSRF": "#ea580c",
            "BOLA": "#ca8a04",
            "Broken Authentication": "#16a34a",
            "Path Traversal": "#2563eb",
            "Command Injection": "#9333ea",
            "Data Exposure": "#c026d3",
            "PII Data": "#e11d48"
        };
        return colorMap[attackType] || "#6b7280";
    }, []);

    // Helper function to extract country center and name from topology
    const getCountryCenter = useCallback((topology, countryCode) => {
        if (!topology || !countryCode) return null;

        // Find the country feature from topology
        const feature = topology.objects?.default?.geometries?.find(
            geo => geo.properties?.['iso-a2'] === countryCode.toUpperCase() ||
                   geo.properties?.['hc-a2'] === countryCode.toUpperCase()
        );

        if (!feature) return null;

        // Get coordinates and name from properties
        const properties = feature.properties;
        if (properties?.['hc-middle-lon'] && properties?.['hc-middle-lat']) {
            return {
                coords: [properties['hc-middle-lon'], properties['hc-middle-lat']],
                name: properties?.['name'] || countryCode
            };
        }

        return null;
    }, []);

    const fetchMapData = useCallback(async () => {
        try {
            const topology = await fetch(
                "https://code.highcharts.com/mapdata/custom/world.topo.json"
            ).then((response) => response.json());

            // Extract coordinates from topology for each attack flow
            const flowsWithCoords = attackRequests.map(attack => {
                const sourceData = getCountryCenter(topology, attack.sourceCountry);
                const destData = getCountryCenter(topology, attack.destinationCountry);

                if (!sourceData || !destData) return null;

                return {
                    ...attack,
                    sourceCoords: sourceData.coords,
                    destCoords: destData.coords,
                    sourceName: sourceData.name,
                    destinationName: destData.name
                };
            }).filter(f => f !== null);

            // Prepare marker data for source and destination points
            const sourceMarkers = flowsWithCoords.map(attack => ({
                name: attack.sourceName,
                geometry: {
                    type: 'Point',
                    coordinates: attack.sourceCoords
                },
                custom: {
                    attackType: attack.attackType,
                    count: attack.count
                }
            }));

            const destinationMarkers = flowsWithCoords.map(attack => ({
                name: attack.destinationName,
                geometry: {
                    type: 'Point',
                    coordinates: attack.destCoords
                }
            }));

            // Create line series data using mapline type
            const lineSeriesData = flowsWithCoords.map((attack) => ({
                geometry: {
                    type: 'LineString',
                    coordinates: [
                        attack.sourceCoords,
                        attack.destCoords
                    ]
                },
                color: getColorForAttackType(attack.attackType),
                className: 'animated-attack-line',
                custom: {
                    attackType: attack.attackType,
                    source: attack.sourceName,
                    destination: attack.destinationName,
                    count: attack.count
                }
            }));

            Highcharts.mapChart("attack-world-map-container", {
                chart: {
                    map: topology,
                    backgroundColor: "#fff",
                    animation: true
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
                    enabled: true,
                    buttons: {
                        contextButton: {
                            menuItems: [
                                "viewFullscreen",
                                "separator",
                                "downloadPNG",
                                "downloadJPEG",
                                "downloadPDF",
                                "downloadSVG",
                            ],
                        },
                    },
                },

                plotOptions: {
                    line: {
                        marker: {
                            enabled: false
                        }
                    },
                    mappoint: {
                        animation: {
                            duration: 1000
                        }
                    }
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
                    // Attack flow lines
                    {
                        type: 'mapline',
                        name: 'Attack Flows',
                        data: lineSeriesData,
                        lineWidth: 3,
                        tooltip: {
                            headerFormat: '',
                            pointFormat: '<b>{point.custom.attackType}</b><br/>From: {point.custom.source}<br/>To: {point.custom.destination}'
                        },
                        enableMouseTracking: true
                    },
                    // Source markers (red)
                    {
                        type: "mappoint",
                        name: "Source",
                        data: sourceMarkers,
                        color: '#ff4444',
                        marker: {
                            radius: 6,
                            fillColor: '#ff4444',
                            lineColor: '#fff',
                            lineWidth: 2,
                            symbol: 'circle'
                        },
                        tooltip: {
                            headerFormat: '',
                            pointFormat: '<b>{point.name}</b><br/>Source Location<br/>Attack: {point.custom.attackType}'
                        },
                        zIndex: 10
                    },
                    // Destination markers (blue)
                    {
                        type: "mappoint",
                        name: "Destination",
                        data: destinationMarkers,
                        color: '#4444ff',
                        marker: {
                            radius: 6,
                            fillColor: '#4444ff',
                            lineColor: '#fff',
                            lineWidth: 2,
                            symbol: 'circle'
                        },
                        tooltip: {
                            headerFormat: '',
                            pointFormat: '<b>{point.name}</b><br/>Destination Server'
                        },
                        zIndex: 10
                    }
                ],
            });

        } catch (error) {
            console.error("Error loading map:", error);
        }
    }, [attackRequests, getColorForAttackType]);

    useEffect(() => {
        if (attackRequests && attackRequests.length > 0) {
            // Small delay to ensure DOM is ready
            const timer = setTimeout(() => {
                fetchMapData();
            }, 0);
            return () => clearTimeout(timer);
        } else {
        }
    }, [attackRequests, fetchMapData]);

    return (
        <InfoCard
            title="Guardrail Attack Map"
            titleToolTip="Visual representation of attack requests showing source locations and destination servers"
            component={
                <div style={{ position: 'relative', width: '100%', height: style?.height || '500px' }}>
                    <div id="attack-world-map-container" style={style || { width: "100%", height: "500px" }}></div>
                </div>
            }
        />
    );
}

export default AttackWorldMap;
