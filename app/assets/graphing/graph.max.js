// This file expects a number of variables to be defined globally.
// Use only in conjunction with GraphView.getView
var retryFrequency = 10,
    delay = 0,
    maxDelay = 10000;
var intervalID = setInterval(function() {
    delay += retryFrequency;

    // Ghastly hack: Occasionally, when the graphing javascript runs, document.body has no
    // dimensions, which causes the graph to not actually display full screen, and it also
    // causes extra points to appear on the y axis. If we wait, dimensions will sometimes get
    // populated. If they never do, give up, and user will see a blank space.
    if (!document.getElementById("chart") || !document.body.offsetWidth || !document.body.offsetHeight) {
        if (delay > maxDelay) {
            clearInterval(intervalID);
        }
        return;
    }
    clearInterval(intervalID);

    try {
        // Match graph size to view size
        var titleHeight = (document.getElementById('chart-title') || { offsetHeight: 0 }).offsetHeight;
        config.size = {
            height: document.body.offsetHeight - titleHeight,
            width: document.body.offsetWidth,
        };

        // Turn off default hover/click behaviors
        config.interaction = { enabled: true };
        var formatXTooltip = function(x) {
            return x;
        }
        if (type === "time") {
            formatXTooltip = d3.timeFormat(config.axis.x.tick.format);
        }
        config.tooltip = {
            show: true,
            grouped: type === "bar",
            contents: function(yData, defaultTitleFormat, defaultValueFormat, color) {
                var html = "";

                // Add rows for y values
                for (var i = 0; i < yData.length; i++) {
                    if (data.isData[yData[i].id]) {
                        var yName = config.data.names[yData[i].id];
                        html += "<tr><td>" + yName + "</td><td>" + yData[i].value + "</td></tr>";
                    }
                }
                if (!html) {
                    return "";
                }

                // Add a top row for x value
                if (type === "bar") {
                    html = "<tr><td colspan='2'>" + data.barLabels[yData[0].x] + "</td></tr>" + html;
                } else {
                    html = "<tr><td>" + data.xNames[yData[0].id] + "</td><td>"
                            + formatXTooltip(yData[0].x) + "</td></tr>"
                            + html;
                }

                html = "<table>" + html + "</table>";
                html = "<div id='tooltip'>" + html + "</div>";
                return html;
            },
        };

        // Set point size for bubble charts, and turn points off altogether
        // for other charts (we'll be using custom point shapes)
        config.point = {
            r: function(d) {
                if (data.radii[d.id]) {
                    // Arbitrary max size of 30
                    return 30 * data.radii[d.id][d.index] / data.maxRadii[d.id];
                }
                return 0;
            },
            focus: {
                expand: {
                  enabled: false
                }
            },
        };

        // Add functions for custom tick label text (where foo-labels was an object).
        // Don't do this if the tick format was already set by Java (which will
        // only happen for time-based graphs that are NOT using custom tick text).
        if (config.axis.x.tick && (config.axis.x.tick.values || config.axis.x.tick.count) && !config.axis.x.tick.format) {
            config.axis.x.tick.format = function(d) {
                var key = String(d);
                if (type === "time") {
                    var time = key.match(/\d+:\d+:\d+/)[0];
                    key = d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate() + " " + time;

                }
                var label = axis.xLabels[key] === undefined ? d : axis.xLabels[key];
                var returnVal = String(Math.round(label) || label);
                if (typeof characterLimit !== "undefined") {
                    var limit = parseInt(characterLimit);
                    if (returnVal.length > limit) { // This is coupled with StringExtensionImpl#getWidth()
                        return String(returnVal).slice(0, limit - 3) + "...";
                    } else {
                        return returnVal;
                    }
                } else {
                    return returnVal;
                }
            };
        }
        if (config.axis.y.tick && (config.axis.y.tick.values || config.axis.y.tick.count)) {
            config.axis.y.tick.format = function(d) {
                return axis.yLabels[String(d)] || Math.round(d);
            };
        }
        if (config.axis.y2.tick) {
            config.axis.y2.tick.format = function(d) {
                return axis.y2Labels[String(d)] || Math.round(d);
            };
        }

        // Hide any system-generated series from legend
        var hideSeries = [];
        for (var yID in config.data.xs) {
            if (!data.isData[yID]) {
                hideSeries.push(yID);
            }
        }
        config.legend.hide = hideSeries;

        // Configure data labels, which we use as intended and also to display annotations
        var showDataLabels = !!config.data.labels;
        config.data.labels = {
            format: function(value, id, index) {
                if (showDataLabels && data.isData[id]) {
                    return value || '';
                } else {
                  return data.annotations[id] || '';
                }
            },
        };

        // Don't use C3's default ordering for stacked series, use the order series are defined
        config.data.order = false;

        // Post-processing
        config.onrendered = function() {
            // Support point-style
            for (var yID in data.pointStyles) {
                var symbol = data.pointStyles[yID];
                applyPointShape(yID, symbol);
                applyLegendShape(yID, symbol);
            }

            // Configure colors more specifically than C3 allows
            for (var yID in config.data.colors) {
                // Data itself
                if (type === "bar") {
                    var bars = d3.selectAll(".c3-bars-" + yID + " path")["_groups"][0];
                    for (var i = 0; i < bars.length; i++) {
                        // If there's a bar-specific color, set it
                        if (data.barColors[yID] && data.barColors[yID][i]) {
                            bars[i].style.fill = data.barColors[yID][i];
                        }
                        // Get opacity: bar-specific if it's there, otherwise series-specific
                        var opacity;
                        if (data.barOpacities[yID]) {
                            opacity = data.barOpacities[yID][i];
                        }
                        opacity = opacity || data.lineOpacities[yID];
                        bars[i].style.opacity = opacity;
                    }
                } else {
                    var line = d3.selectAll(".c3-lines-" + yID + " path")["_groups"][0][0];
                    if (line) {
                        line.style.opacity = data.lineOpacities[yID];
                    }
                }

                // Legend
                var legend = d3.selectAll(".c3-legend-item-" + yID + " path")["_groups"][0];
                if (!legend.length) {
                    legend = d3.selectAll(".c3-legend-item-" + yID + " line")["_groups"][0];
                }
                if (legend.length) {
                    legend = legend[0];
                    legend.style.opacity = data.lineOpacities[yID];
                    if (data.barColors[yID] && data.barColors[yID][0]) {
                        legend.style.stroke =  data.barColors[yID][0];
                    }
                }

                // Point shapes
                var points = d3.selectAll(".c3-circles-" + yID + " path")["_groups"][0];
                if (!points.length) {
                    points = d3.selectAll(".c3-circles-" + yID + " circle")["_groups"][0];
                }
                for (var i = 0; i < points.length; i++) {
                    points[i].style.opacity = data.lineOpacities[yID];
                }
            }
            for (var yID in data.areaColors) {
                var area = d3.selectAll(".c3-areas-" + yID + " path")["_groups"][0][0];
                if (area) {
                    area.style.fill = data.areaColors[yID];
                    area.style.opacity = data.areaOpacities[yID];
                }
            }

            // The android <=> javascript interface sometimes has a loading delay.
            // Wait for it.
            var androidDelay = 0;
            var androidIntervalID = setInterval(function() {
                androidDelay += retryFrequency;
                if (!window.Android) {
                    if (androidDelay > maxDelay) {
                        clearInterval(androidIntervalID);
                    }
                    return;
                }

                clearInterval(androidIntervalID);
                Android.run();
            });
        };

        // Generate chart
        c3.generate(config);
    } catch(e) {
        displayError(e);
    }
}, retryFrequency);

/**
 * Replace C3's default circle points with user-requested symbols.
 * @param yID String ID of y-values to manipulate
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 */
function applyPointShape(yID, symbol) {
    if (type === 'bar' || type === 'bubble') {
        return;
    }

    var circleSet = d3.selectAll(".c3-circles-" + yID);
    var circles = circleSet.selectAll("circle")["_groups"][0];
    if (!circles) {
        return;
    }

    for (var j = 0; j < circles.length; j++) {
        circles[j].style.opacity = 0;    // hide default circle
        appendSymbol(
            circleSet,
            circles[j].cx.baseVal.value,
            circles[j].cy.baseVal.value,
            symbol,
            circles[j].style.color
        );
    }
}

/**
 * Make shape displayed in legend match shape used on line.
 * @param yID String ID of y-values to manipulate
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 */
function applyLegendShape(yID, symbol) {
    if (symbol !== "none") {
        var legendItem = d3.selectAll(".c3-legend-item-" + yID);
        var line = legendItem.selectAll("line")["_groups"];    // there will only be one line
        if (!line || !line.length) {
            return;
        }
        line = line[0][0]
        line.style.opacity = 0;    // hide default square
        appendSymbol(
            legendItem,
            line.x1.baseVal.value + 5,
            line.y1.baseVal.value,
            symbol,
            line.style.stroke
        );
    }
}

/**
 * Add symbol to given element.
 * @param parent Element to attach symbol to
 * @param x x-coordinate to draw at
 * @param y y-coordinate to draw at
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 * @param color Color to draw symbol
 */
function appendSymbol(parent, x, y, symbol, color) {
    if (symbol === 'none') {
        return;
    }

    // String symbol doesn't work, need to map https://github.com/d3/d3-shape/issues/64
    var d3Symbol = d3.symbolCircle;
    switch(symbol) {
        case "cross":
            d3Symbol = d3.symbolCross;
            break;
        case "diamond":
            d3Symbol = d3.symbolDiamond;
            break;
        case "triangle-up":
            d3Symbol = d3.symbolTriangle;
            break;
        case "triangle-down": // D3 doesn't have a triangle-down anymore
            d3Symbol = d3.symbolTriangle;
            break;
        case "square":
            d3Symbol = d3.symbolSquare;
            break;
        case "star":
            d3Symbol = d3.symbolStar;
            break;
        case "wye":
            d3Symbol = d3.symbolWye;
            break;
        default:
            d3Symbol = d3.symbolCircle;
            break;
    }

    parent.append("path")
        .attr("transform", function (d) {
        return "translate(" + x + ", " + y + ")";
    })
        .attr("class", "symbol")
        .attr("d", d3.symbol()
              .type(d3Symbol)
              .size(50))
        .style("fill", color)
        .style("stroke", color);
}

/**
 * Display JavaScript error on device.
 */
function displayError(message) {
    console.log(message);
    var error = document.getElementById('error');
    error.innerHTML = message;
    error.style.display = 'block';
}
