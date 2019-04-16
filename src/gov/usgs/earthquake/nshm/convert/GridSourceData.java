package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.NORMAL;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.REVERSE;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.STRIKE_SLIP;
import static gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling.NSHM_POINT_WC94_LENGTH;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addAttribute;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addComment;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addElement;
import static gov.usgs.earthquake.nshmp.internal.Parsing.enumValueMapToString;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.A;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.B;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.FOCAL_MECH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ID;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAGS;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAG_DEPTH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAX_DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.M_MAX;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RATE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RATES;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RUPTURE_SCALING;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.STRIKE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.TYPE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WEIGHT;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.DEFAULT_MFDS;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.GRID_SOURCE_SET;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.INCREMENTAL_MFD;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.NODE;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.NODES;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.SETTINGS;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.SOURCE_PROPERTIES;
import static gov.usgs.earthquake.nshmp.mfd.MfdType.GR;
import static gov.usgs.earthquake.nshmp.mfd.MfdType.GR_TAPER;
import static gov.usgs.earthquake.nshmp.mfd.MfdType.INCR;
import static gov.usgs.earthquake.nshmp.mfd.MfdType.SINGLE;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshm.util.FaultCode;
import gov.usgs.earthquake.nshm.util.RateType;
import gov.usgs.earthquake.nshm.util.Utils;
import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.eq.fault.FocalMech;
import gov.usgs.earthquake.nshmp.geo.GriddedRegion;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.mfd.IncrementalMfd;
import gov.usgs.earthquake.nshmp.mfd.MfdType;
import gov.usgs.earthquake.nshmp.mfd.Mfds;

/*
 * Grid source data container.
 * 
 * @author Peter Powers
 */
class GridSourceData {

  String name;
  int id;
  double weight;

  double[] depths;
  double depthMag;
  double maxDepth;
  Map<FocalMech, Double> mechWtMap;

  GR_Data grDat;
  CH_Data chDat;

  double dR, rMax;

  double minLat, maxLat, dLat;
  double minLon, maxLon, dLon;

  FaultCode fltCode;
  boolean bGrid, mMaxGrid, weightGrid;
  double mTaper;

  // we're now ignoring mTaper in favor of using
  // incremental MFDs where appropriate/necessary

  URL aGridURL, bGridURL, mMaxGridURL, weightGridURL;

  double timeSpan;
  RateType rateType;

  double strike = Double.NaN;

  GriddedRegion region;
  double[] aDat, bDat, mMaxDat, wgtDat;

  private static final String LF = System.getProperty("line.separator");

  // @formatter:off

  public void writeCsv(Path out) throws IOException {
    if (name.contains("2007all8")) {
      List<String> cratonLines = new ArrayList<>();
      List<String> marginLines = new ArrayList<>();
      List<String> defaultLines = new ArrayList<>();
      cratonLines.add("lon,lat,a,b");
      marginLines.add("lon,lat,a,b");
      defaultLines.add("lon,lat,a,b");
      createLargeCeusGridCsv(cratonLines, marginLines, defaultLines);
      Files.write(out.resolve("craton.csv"), cratonLines);
      Files.write(out.resolve("margin.csv"), marginLines);
      Files.write(out.resolve("default.csv"), defaultLines);
    }
  }
  
	/**
	 * Write grid data to XML.
	 * 
	 * @param out file
	 * @throws ParserConfigurationException
	 * @throws TransformerConfigurationException
	 * @throws TransformerException
	 */
	public void writeXML(File out) throws ParserConfigurationException,
			TransformerConfigurationException, TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(GRID_SOURCE_SET.toString());
		addAttribute(NAME, name, root);
		addAttribute(ID, id, root);
		addAttribute(WEIGHT, weight, root);
		Converter.addDisclaimer(root);
		addComment(" Original source file: " + name + " ", root);
		doc.appendChild(root);
		
		if (chDat != null) { // single mag defaults e.g. charleston
			writeSingleMagGrid(root);
		} else if (name.contains("2007all8")) { // large all8 CEUS grids
			writeLargeCeusGrid(root);
		} else if (weightGrid) { // WUS grids with downweighted rates above 6.5 in CA
			writeMixedGrid(root);
		} else {
			writeStandardGrid(root);
		}
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);

		trans.transform(source, result);
	}
	
	// single magnitude grids (e.e.g Charleston)
	private void writeSingleMagGrid(Element root) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		chDat.appendTo(mfdRef, null);
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			double singleMagRate = Mfds.incrRate(aVal, grDat.bVal, chDat.mag);
			addAttribute(RATE, singleMagRate, "%.8g", nodeElem);
			addAttribute(TYPE, SINGLE, nodeElem);
		}
	}
	
	// large mblg CEUS grids with craton-margin tapers etc... to CSV
	 private void createLargeCeusGridCsv(
	     List<String> cratonLines,
       List<String> marginLines,
       List<String> defaultLines) {
	   initMasks();
	   for (int i=0; i<aDat.length; i++) {
       double aVal = aDat[i];
       if (aVal <= 0.0) continue;
       double bVal = bDat[i];
       String bStr = DoubleMath.fuzzyEquals(bVal, 0.95, 0.001) ? "" : String.format("%.2f", bVal);
       Location loc = region.locationForIndex(i);
       boolean craton = cratonFlags[i];
       boolean margin = marginFlags[i];
       String line = String.format(
           "%.1f,%.1f,%.8e,%s", // ,%.2f
           loc.lon(), loc.lat(), aVal, bStr); // , bVal, mMaxDat[i]
       if (craton) {
         cratonLines.add(line);
       } else if (margin) {
         marginLines.add(line);
       } else {
         defaultLines.add(line);
       }
	  }
	}

	// large mblg CEUS grids with craton-margin tapers etc...
	private void writeLargeCeusGrid(Element root) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		Element e =  addElement(INCREMENTAL_MFD, mfdRef);
		addAttribute(TYPE, INCR, e);
		List<Double> mags = Doubles.asList(name.contains(".AB.") ? abMags : jMags);
		addAttribute(MAGS, Parsing.toString(mags, "%.2f"), e);
		List<Double> rates = Doubles.asList(new double[mags.size()]);
		addAttribute(RATES, Parsing.toString(rates, "%.1f"), e);
		addAttribute(WEIGHT, grDat.weight, e);
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			addCEUS_MFD(i, nodeElem);
			addAttribute(TYPE, INCR, nodeElem);
		}
	}
	
	// for grids with wtGrid
	private void writeMixedGrid(Element root) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		grDat.appendTo(mfdRef, null);
		Element e = addElement(INCREMENTAL_MFD, mfdRef);
		addAttribute(TYPE, INCR, e);
		// default mags go up to default grid mMax; mags will be overridden
		// where node mMax is higher
		double[] mags = Data.buildSequence(grDat.mMin, grDat.mMax, 0.1, true);
		double[] rates = new double[mags.length];
		addAttribute(MAGS, Parsing.toString(Doubles.asList(mags), "%.2f"), e);
		addAttribute(RATES, Parsing.toString(Doubles.asList(rates), "%.1f"), e);
		addAttribute(WEIGHT, grDat.weight, e);
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));

			double nodeWt = wgtDat[i];
			boolean wtIsOne = DoubleMath.fuzzyEquals(nodeWt, 1.0, 0.00000001);
			// weight doesn't apply because mMax <= mTaper
			boolean ignoreWt = mMaxDat[i] <= mTaper;
			if (wtIsOne || ignoreWt) {
				writeStandardMFDdata(nodeElem, i);
				addAttribute(TYPE, GR, nodeElem);
			} else {
				addWUS_MFD(i, nodeElem);
				addAttribute(TYPE, INCR, nodeElem);
			}
		}
	}
	
	// standard grid without customizations requiring incremental MFDs
	private void writeStandardGrid(Element root) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		grDat.appendTo(mfdRef, null);
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			writeStandardMFDdata(nodeElem, i);
		}
	}
	
	private void writeStandardMFDdata(Element nodeElem, int i) {
		MfdType type = grDat.cMag > 6.5 ? GR_TAPER : GR;
		addAttribute(TYPE, type, nodeElem);
		addAttribute(A, aDat[i], "%.8g", nodeElem);
		if (bGrid) {
			double nodebVal = bDat[i];
			if (!DoubleMath.fuzzyEquals(nodebVal, grDat.bVal, 0.000001)) {
				addAttribute(B, nodebVal, "%.6f", nodeElem);
			}
		}
		if (mMaxGrid) {
			double nodeMMax = mMaxDat[i] - grDat.dMag / 2.0;
			if (!DoubleMath.fuzzyEquals(nodeMMax, grDat.mMax, 0.000001) && nodeMMax != 0.0) {
				addAttribute(M_MAX, nodeMMax, "%.6f", nodeElem);
			}
		}
	}
	
	// source attribute settings
	private void addSourceProperties(Element settings) {
		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(MAG_DEPTH_MAP, magDepthDataToString(depthMag, depths), propsElem);
		addAttribute(MAX_DEPTH, maxDepth, propsElem);
		addAttribute(FOCAL_MECH_MAP, enumValueMapToString(mechWtMap), propsElem);
		addAttribute(STRIKE, strike, propsElem);
		addAttribute(RUPTURE_SCALING, NSHM_POINT_WC94_LENGTH, propsElem);
	}
			
	/*
	 * This actually reproduces something closer to the originally supplied
	 * NSHMP mag-depth-weight distribution, but it's not worth going back to
	 * the parser to change it. Example outputs that can be parsed as
	 * stringToValueValueWeightMap:
	 * 		[6.5::[5.0:1.0]; 10.0::[1.0:1.0]]	standard two depth
	 * 		[10.0::[50.0:1.0]]					standard single depth
	 */
	static String magDepthDataToString(double mag, double[] depths) {
		StringBuffer sb = new StringBuffer("[");
		if (DoubleMath.fuzzyEquals(depths[0], depths[1], 0.000001)) {
			sb.append("10.0::[").append(depths[0]);
			sb.append(":1.0]]");
		} else {
			sb.append(mag).append("::[");
			sb.append(depths[0]).append(":1.0]; 10.0::[");
			sb.append(depths[1]).append(":1.0]]");
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append("Grid Config").append(LF)
			.append("            Name: ").append(name).append(LF)
			.append("       Lat range: ").append(minLat).append(" ").append(maxLat).append(LF)
			.append("       Lon range: ").append(minLon).append(" ").append(maxLon).append(LF)
			.append("     [dLat dLon]: ").append(dLat).append(" ").append(dLon).append(LF)
			.append("   Rup top M<6.5: ").append(depths[0]).append(LF)
			.append("   Rup top M≥6.5: ").append(depths[1]).append(LF)
			.append("    Mech weights: ")
			.append("SS=").append(mechWtMap.get(STRIKE_SLIP))
			.append(" R=").append(mechWtMap.get(REVERSE))
			.append(" N=").append(mechWtMap.get(NORMAL)).append(LF)
			.append("   opt [dR rMax]: ").append(dR).append(" ").append(rMax).append(LF);
		if (chDat != null) {
			sb.append("    SINGLE [a M]: ").append(chDat.rate).append(" ").append(chDat.mag).append(LF);
		} else {
			sb.append(" GR [b M- M+ dM]: ").append(grDat.bVal).append(" ").append(grDat.mMin)
				.append(" ").append(grDat.mMax).append(" ").append(grDat.dMag).append(LF);
		}
		sb.append("          a grid: ").append(aGridURL.toString()).append(LF)
			.append("          b grid: ").append(bGrid)
			.append(" ").append((bGridURL != null) ? bGridURL.toString() : "").append(LF)
			.append("       mMax grid: ").append(mMaxGrid)
			.append(" ").append((mMaxGridURL != null) ? mMaxGridURL.toString() : "").append(LF)
			.append("     weight grid: ").append(weightGrid)
			.append(" ").append((weightGridURL != null) ? weightGridURL.toString() : "").append(LF)
			.append("         M taper: ").append(mTaper).append(LF)
			.append("       Time span: ").append(timeSpan).append(LF)
			.append("            Rate: ").append(rateType).append(LF)
			.append("      Fault Code: ").append(fltCode).append(LF)
			.append("          Strike: ").append(strike).append(LF);
		return sb.toString();
	}
	
	
	/////////////// WUS Incremental ///////////////
	
	// there are many nodes where mMax = 6.5 and a weight is applied
	// that ultimately is unnecessar; build incremental default mags
	// based on mMax of file; if node mMax is greater, then add mags
	// attribute to it
	
	private void addWUS_MFD(int i, Element node) {
		double cutoffMax = mMaxDat[i] <= 0 ? grDat.mMax + grDat.dMag / 2. : mMaxDat[i];
		double nodeMax = mMaxDat[i] <= 0 ? grDat.mMax : mMaxDat[i] - grDat.dMag / 2.0;
		double mfdMax = Math.max(grDat.mMax, nodeMax);
		
//		if (nodeMax <= grDat.mMax) {
		// mfdMax is either gridMax or some higher value
		double bVal = bGrid ? bDat[i] : grDat.bVal;
		GR_Data grNode = GR_Data.create(aDat[i], bVal, grDat.mMin, mfdMax, grDat.dMag, 1.0);
		IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(
			grNode.mMin, grNode.dMag, grNode.nMag, grNode.bVal, 1.0);
		mfd.scaleToIncrRate(grNode.mMin, Mfds.incrRate(grNode.aVal, grNode.bVal, grNode.mMin));
		if (cutoffMax <= mfdMax) mfd.zeroAboveMag2(cutoffMax);
		wusScaleRates(mfd, i);
		// if node mMax <= gridMax add rates for defualt mags as atts
		addAttribute(RATES, Parsing.toString(mfd.yValues(), "%.8g"), node);
		// if node mMax > gridMax add mags as atts as well
		if (mfdMax > grDat.mMax) {
			addAttribute(MAGS, Parsing.toString(mfd.xValues(), "%.2f"), node);
		}
	}

	private void wusScaleRates(IncrementalMfd mfd, int idx) {
		for (int i = 0; i < mfd.getNum(); i++) {
			if (mfd.getX(i) > mTaper) mfd.set(i, mfd.getY(i) * wgtDat[idx]);
		}
	}
	
	/////////////// CEUS Customizations ///////////////
	
	private void addCEUS_MFD(int i, Element node) {
	
		// use fixed value if mMax matrix value was 0
		// for the large CEUS sources, we're going to fix mMax at it's
		// highest possible value (in this case 7.35 for AB and 7.15 for J);
		// the AB mMax grids specify 7.45 but that bin is always zeroed out
		// by the craton-margin scale factors and would be skipped in any event
		// due to GR bin recentering
		
		double cutoffMax = mMaxDat[i] <= 0 ? grDat.mMax + grDat.dMag / 2. : mMaxDat[i];
		double nodeMax = mMaxDat[i] <= 0 ? grDat.mMax : mMaxDat[i] - grDat.dMag / 2.0;
		double mfdMax = name.contains(".AB.") ? abMax : jMax;
		
		GR_Data grNode = GR_Data.create(aDat[i], bDat[i], grDat.mMin, mfdMax, grDat.dMag, 1.0);
		IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(
			grNode.mMin, grNode.dMag, grNode.nMag, grNode.bVal, 1.0);
		// a-value is stored as log10(a)
		mfd.scaleToIncrRate(grNode.mMin, Mfds.incrRate(grNode.aVal, grNode.bVal, grNode.mMin));
		if (cutoffMax < mfdMax) mfd.zeroAboveMag2(cutoffMax);
		ceusScaleRates(mfd, i);
		addAttribute(RATES, Parsing.toString(mfd.yValues(), "%.8g"), node);
	}

	private static double jMax = 7.15;
	private static double abMax = 7.35;
	private static double[] jMags = Data.buildSequence(5.05, jMax, 0.1, true); //{5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65, 5.75, 5.85, 5.95, 6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95, 7.05, 7.15 };
	private static double[] abMags = Data.buildSequence(5.05, abMax, 0.1, true); //{5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65, 5.75, 5.85, 5.95, 6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95, 7.05, 7.15, 7.25, 7.35 };
	// wtmj_cra: full weight up to 6.55; Mmax=6.85 @ 0.2 wt
	// wtmj_ext: full weight up to 6.85; Mmax=7.15 @ 0.2 wt
	// wtmab_cra: full weight up to 6.75; Mmax=7.05 @ 0.2 wt
	// wtmab_ext: full weight up to 7.15; Mmax=7.35 @ 0.2 wt
	// NOTE the 7.45 bin was removed (all zeros) pmpowers 11/15/2103
	private static double[] wtmj_cra =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0 };
	private static double[] wtmj_ext =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.7, 0.2, 0.0, 0.0 };
	private static double[] wtmab_cra = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9, 0.7, 0.2, 0.0, 0.0, 0.0 };
	private static double[] wtmab_ext = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2 };
	private static boolean[] cratonFlags;
	private static boolean[] marginFlags;
	
	
	private void ceusScaleRates(IncrementalMfd mfd, int idx) {
		initMasks();
		boolean craFlag = cratonFlags[idx];
		boolean marFlag = marginFlags[idx];
		if ((craFlag | marFlag) == false) return;
		double[] weights = name.contains(".AB.") ? 
			(craFlag ? wtmab_cra : wtmab_ext) :
				(craFlag ? wtmj_cra : wtmj_ext);
		applyWeight(mfd, weights);
	}
	
	private void applyWeight(IncrementalMfd mfd, double[] weights) {
		for (int i=0; i<mfd.getNum(); i++) {
			double weight = weights[i];
			if (weight == 1.0) continue;
			mfd.set(i, mfd.getY(i) * weight);
		}
	}

	private void initMasks() {
		// this is only used for CEUS so we don't have to worry about having
		// the wrong dimensions set for these static fields
		if (cratonFlags == null) {
			URL craton = SourceManager_2008.getCEUSmask("craton");
			URL margin = SourceManager_2008.getCEUSmask("margin");
			int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
			int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
			cratonFlags = Utils.readBoolGrid(craton, nRows, nCols);
			marginFlags = Utils.readBoolGrid(margin, nRows, nCols);
		}
	}

}
