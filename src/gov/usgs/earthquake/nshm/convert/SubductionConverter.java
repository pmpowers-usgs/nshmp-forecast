package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.SourceElement.GEOMETRY;
import static org.opensha.eq.forecast.SourceElement.LOWER_TRACE;
import static org.opensha.eq.forecast.SourceElement.SUBDUCTION_SOURCE;
import static org.opensha.eq.forecast.SourceElement.SUBDUCTION_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.TRACE;
import static org.opensha.util.Parsing.addElement;
import static org.opensha.util.Parsing.readInt;

import gov.usgs.earthquake.nshm.util.MFD_Type;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.MFDs;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

/*
 * Converts NSHMP subduction configuration files.
 * @author Peter Powers
 */
class SubductionConverter {

	private static Logger log;
	static {
		log = Utils.logger();
		Level level = Level.INFO;
		log.setLevel(level);
		for (Handler h : Utils.logger().getHandlers()) {
			h.setLevel(level);
		}
	}
	
	private SubductionConverter() {}
	
	static void convert(SourceFile sf, String dir) {
		
		try {
			log.info("Source file: " + sf.name + " " + sf.region + " " + sf.weight);
			Exporter export = new Exporter();
			export.file = sf.name;	
			export.weight = sf.weight;
	
			Iterator<String> lines = sf.lineIterator();
	
			// skip irrelevant header data
			skipSiteData(lines);
			skipGMMs(lines);
			lines.next(); // distance sampling on fault and dMove
			lines.next(); // rMax and discretization
			
			while (lines.hasNext()) {
				
				// collect data on source name line
				SourceData fDat = new SourceData();
				List<String> srcInfo = Parsing.toStringList(lines.next());
				MFD_Type mfdType = MFD_Type.typeForID(Integer.valueOf(srcInfo.get(0)));
				fDat.file = sf;
				fDat.focalMech = Utils.typeForID(Integer.valueOf(srcInfo.get(1)));

				try {
					// hazSUBXngatest: read a 3rd value for mfd count
					fDat.nMag = Integer.valueOf(srcInfo.get(2));
					fDat.name = Joiner.on(' ').join(Iterables.skip(srcInfo, 3));
				} catch (NumberFormatException nfe) {
					// hazSUBXnga: if can't read 3rd int, set name and nMag to 1
					fDat.nMag = 1;
					fDat.name = Joiner.on(' ').join(Iterables.skip(srcInfo, 2));
				}
	
				List<String> mfdSrcDat =  Parsing.toLineList(lines, fDat.nMag);
				generateMFDs(fDat, mfdType, mfdSrcDat);
				
				generateTraces(lines, fDat);
				
				if (fDat.mfds.size() == 0) {
					log.severe("Source with no mfds");
					System.exit(1);
				}
				export.srcMap.put(fDat.name, fDat);
			}
			
			String S = File.separator;
			String outPath = dir + S + sf.region + S + sf.type + S + 
					sf.name.substring(0, sf.name.lastIndexOf('.')) + ".xml";
			File outFile = new File(outPath);
			Files.createParentDirs(outFile);
			export.writeXML(new File(outPath));
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Fault parse error: exiting", e);
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	private static void generateMFDs(SourceData ss, MFD_Type type, List<String> lines) {
		// for 2008 NSHMP all sub sources are entered as floating GR, however
		// any M8.8 or greater events are rupture filling, pseudo-char; 2014
		// makes distinction between GR and CH
		for (String line : lines) {
			if (type == MFD_Type.GR) {
				GR_Data gr = GR_Data.createForSubduction(line);
				if (gr.nMag > 1) {
					gr.floats = true;
					ss.mfds.add(gr);
					log(ss, MFD_Type.GR, gr.floats);
					// TODO this is awful logging
					// log record should get everything from MFD_Data or Data
				} else {
					// in 2008 CH ruptures were difined with GR trickery
					CH_Data ch = CH_Data.create(gr.mMin, MFDs.grRate(gr.aVal,
						gr.bVal, gr.mMin), gr.weight);
					ss.mfds.add(ch);
					log(ss, MFD_Type.CH, ch.floats);
				}
			} else if (type == MFD_Type.CH) {
				CH_Data ch = CH_Data.create(
					Parsing.readDouble(line, 0),
					Parsing.readDouble(line, 1),
					Parsing.readDouble(line, 2));
				ss.mfds.add(ch);
				log(ss, MFD_Type.CH, ch.floats);
			} else {
				throw new IllegalArgumentException("MFD_Type not supported");
			}
		}
	}

	private static void log(SourceData fd, MFD_Type mfdType, boolean floats) {
		String mfdStr = Strings.padEnd(mfdType.name(), 5, ' ') +
			(floats ? "f " : "  ");
		log.info(mfdStr + fd.name);
	}
	
	private static void generateTraces(Iterator<String> it, SourceData ss) {
		ss.upperTrace = generateTrace(it, readInt(it.next(), 0));
		ss.lowerTrace = generateTrace(it, readInt(it.next(), 0));
	}
	
	private static LocationList generateTrace(Iterator<String> it, int size) {
		List<String> traceDat = Parsing.toLineList(it, size);
		List<Location> locs = Lists.newArrayList();
		for (String ptDat : traceDat) {
			List<Double> latlon = Parsing.toDoubleList(ptDat);
			locs.add(Location.create(latlon.get(0), latlon.get(1), latlon.get(2)));
		}
		return LocationList.create(locs);
	}
	
	private static void skipSiteData(Iterator<String> it) {
		int numSta = Parsing.readInt(it.next(), 0); // grid of sites or station list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30) and Campbell basin depth
	}

	// GMM data order in subduction is different than for faults
	private static void skipGMMs(Iterator<String> it) {
		int nP = Parsing.readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			it.next(); // period
			it.next(); // out file
			int nAR = readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
			it.next(); // num ground motion values
			it.next(); // ground motion values
		}
	}
	
	/* Wrapper class for individual sources */
	static class SourceData {
		SourceFile file;
		List<MFD_Data> mfds = Lists.newArrayList();
		FocalMech focalMech;
		int nMag;
		String name;
		LocationList upperTrace;
		LocationList lowerTrace;
	}
	
	static class Exporter {
		
		String file = null;
		double weight = 1.0;
		Map<String, SourceData> srcMap = Maps.newLinkedHashMap();
		
		public void writeXML(File out) throws 
				ParserConfigurationException,
				TransformerException {

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			// root elements
			Document doc = docBuilder.newDocument();
			Element root = doc.createElement(SUBDUCTION_SOURCE_SET.toString());
			doc.appendChild(root);
			root.setAttribute("file", file);
			root.setAttribute("weight", Double.toString(weight));

			for (Entry<String , SourceData> entry : srcMap.entrySet()) {
				Element src = addElement(SUBDUCTION_SOURCE, root);
				src.setAttribute("name", entry.getKey());

				SourceData sDat = entry.getValue();

				// MFDs
				for (MFD_Data mfdDat : sDat.mfds) {
					mfdDat.appendTo(src);
				}

				// append geometry from first entry
				Element geom = addElement(GEOMETRY, src);
				geom.setAttribute("rake", Double.toString(sDat.focalMech.rake()));
				Element trace = addElement(TRACE, geom);
				trace.setTextContent(sDat.upperTrace.toString());
				Element lowerTrace = addElement(LOWER_TRACE, geom);
				lowerTrace.setTextContent(sDat.lowerTrace.toString());
			}

			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer trans = transformerFactory.newTransformer();
			trans.setOutputProperty(OutputKeys.INDENT, "yes");
			trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(out);
			trans.transform(source, result);
		}
		
	}

}