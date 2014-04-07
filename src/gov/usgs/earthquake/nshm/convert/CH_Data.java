package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.SourceAttribute.A;
import static org.opensha.eq.forecast.SourceAttribute.FLOATS;
import static org.opensha.eq.forecast.SourceAttribute.M;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST;
import static org.opensha.mfd.MFD_Type.SINGLE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.SourceElement;
import org.w3c.dom.Element;

/*
 * Wrapper for characteristic (single) MFD data.
 * @author Peter Powers
 */
class CH_Data implements MFD_Data {
	
	double mag;
	double rate;
	double weight;
	boolean floats;
	MagScalingType scaling;
		
	private CH_Data(double mag, double rate, double weight, boolean floats, MagScalingType scaling) {
		this.mag = mag;
		this.rate = rate;
		this.weight = weight;
		this.floats = floats;
		this.scaling = scaling;
	}
	
	static CH_Data create(double mag, double rate, double weight, boolean floats,
			MagScalingType scaling) {
		return new CH_Data(mag, rate, weight, floats, scaling);
	}

	@Override
	public Element appendTo(Element parent, MFD_Data ref) {
		Element e = addElement(MAG_FREQ_DIST, parent);
		// always include type
		addAttribute(TYPE, SINGLE, e);
		// always include rate
		addAttribute(A, rate, "%.8g", e);
		if (ref != null) {
			CH_Data refCH = (CH_Data) ref;
			if (mag != refCH.mag) addAttribute(M, mag, "%.3f", e);
			if (floats != refCH.floats) addAttribute(FLOATS, floats, e);
			if (weight != refCH.weight) addAttribute(WEIGHT, weight, e);
			if (scaling != refCH.scaling) addAttribute(MAG_SCALING, scaling.name(), e);
		} else {
			addAttribute(M, mag, "%.3f", e);
			addAttribute(FLOATS, floats, e);
			addAttribute(WEIGHT, weight, e);
			addAttribute(MAG_SCALING, scaling.name(), e);
		}
		return e;
	}
	
}
