package gov.usgs.earthquake.nshm.ak;

import static gov.usgs.earthquake.nshmp.internal.Parsing.addAttribute;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addElement;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ASEIS;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DIP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DIP_DIR;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.INDEX;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.LOWER_DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.GEOMETRY;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.SECTION;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.TRACE;

import gov.usgs.earthquake.nshmp.geo.LocationList;

import org.w3c.dom.Element;

class FaultSection {

  int index;
  String name;
  LocationList trace;
  double aseis;
  double depth;
  double lowerDepth;
  double width;
  double dip;
  double dipDir;
  double slipRate;

  Element appendTo(Element parent) {
    Element sectionElement = addElement(SECTION, parent);
    addAttribute(NAME, name, sectionElement);
    addAttribute(INDEX, index, sectionElement);
    Element geomElement = addElement(GEOMETRY, sectionElement);
    addAttribute(DIP, dip, "%.1f", geomElement);
    addAttribute(DIP_DIR, dipDir, "%.3f", geomElement);
    addAttribute(DEPTH, depth, "%.5f", geomElement);
    addAttribute(LOWER_DEPTH, lowerDepth, "%.5f", geomElement);
    addAttribute(ASEIS, aseis, "%.4f", geomElement);
    Element traceElement = addElement(TRACE, geomElement);
    traceElement.setTextContent(trace.toString());
    return sectionElement;
  }
}
