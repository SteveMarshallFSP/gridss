package au.edu.wehi.socrates;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import au.edu.wehi.socrates.vcf.VcfAttributes;
import au.edu.wehi.socrates.vcf.VcfConstants;
import au.edu.wehi.socrates.vcf.VcfSvConstants;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * VCF Breakend record
 * see Section 5.4.9 of http://samtools.github.io/hts-specs/VCFv4.2.pdf for details of breakends
 * @author Daniel Cameron
 *
 */
public class VariantContextDirectedBreakpoint extends SocratesVariantContext implements DirectedBreakpoint {
	private final BreakendSummary location;
	private final String breakpointSequence;
	private final String anchorSequence;
	private final byte[] breakpointQual;
	public VariantContextDirectedBreakpoint(ProcessingContext processContext, VariantContext context) {
		this(processContext, context, null);
	}
	public VariantContextDirectedBreakpoint(ProcessingContext processContext, VariantContext context, byte[] breakpointQual) {
		super(processContext, context);
		this.breakpointQual = breakpointQual;
		// calculate fields
		List<Allele> altList = getAlternateAlleles();
		if (altList.size() != 1) {
			location = null;
			breakpointSequence = null;
			anchorSequence = null;
			return;
		}
		String alt = getAlternateAllele(0).getDisplayString();
		if (getReference().length() >= alt.length()) {
			// Technically this is valid (eg: {"AAA", "A."} = breakend with deletion), we just can't handle these yet
			location = null;
			breakpointSequence = null;
			anchorSequence = null;
			return;
		}
		if (alt.length() < 2) {
			location = null;
			breakpointSequence = null;
			anchorSequence = null;
			return;
		}
		BreakendDirection direction, remoteDirection = null;
		String localSequence;
		String remoteContig = null;
		if (alt.charAt(0) == '.') {
			// .BreakpointReference
			direction = BreakendDirection.Backward;
			localSequence = alt.substring(1);
		} else if (alt.charAt(alt.length() - 1) == '.') {
			// ReferenceBreakpoint.
			direction = BreakendDirection.Forward;
			localSequence = alt.substring(0, alt.length() - 1);
		} else if (alt.charAt(0) == '[' || alt.charAt(0) == ']') {
			// [Remote[BreakpointReference
			direction = BreakendDirection.Backward;
			remoteDirection = alt.charAt(0) == '[' ? BreakendDirection.Forward : BreakendDirection.Backward;
			String[] split = alt.split("[\\[\\]]");
			remoteContig = split[1];
			localSequence = split[2];
		} else if (alt.charAt(alt.length() - 1) == '[' || alt.charAt(alt.length() - 1) == ']') {
			// ReferenceBreakpoint[Remote[
			direction = BreakendDirection.Forward;
			remoteDirection = alt.charAt(alt.length() - 1) == '[' ? BreakendDirection.Forward : BreakendDirection.Backward;
			String[] split = alt.split("[\\[\\]]");
			remoteContig = split[1];
			localSequence = split[0];
		} else {
			// not breakend!
			location = null;
			breakpointSequence = null;
			anchorSequence = null;
			return;
		}
		int remotePosition = 0;
		if (StringUtils.isNotEmpty(remoteContig)) {
			// flanking square brackets have already been removed
			// format of chr:pos so breakend should always specify a contig position
			String[] components = remoteContig.split(":");
			remoteContig = components[0];
			if (components.length > 1) {
				remotePosition = Integer.parseInt(components[1]);
			}
		}
		int refLength = getReference().length();
		int localPosition;
		if (direction == BreakendDirection.Forward) {
			localPosition = getEnd();
			// anchor - breakpoint
			anchorSequence = localSequence.substring(0, refLength);
			breakpointSequence = localSequence.substring(anchorSequence.length());
		} else {
			localPosition = getStart();
			// breakpoint - anchor
			breakpointSequence = localSequence.substring(0, localSequence.length() - refLength);
			anchorSequence = localSequence.substring(breakpointSequence.length());
		}
		int ciStart = 0, ciEnd = 0;
		if (hasAttribute(VcfSvConstants.CONFIDENCE_INTERVAL_LENGTH_KEY)) {
			int[] ci = (int[])getAttribute(VcfSvConstants.CONFIDENCE_INTERVAL_LENGTH_KEY);
			ciStart = ci[0];
			ciEnd = ci[1];
		}
		if (remoteDirection != null) {
			location = new BreakpointSummary(getReferenceIndex(), direction, localPosition - ciStart, localPosition + ciEnd,
					processContext.getDictionary().getSequenceIndex(remoteContig), remoteDirection, remotePosition - ciStart, remotePosition + ciEnd,
					getEvidence());
		} else {
			location = new BreakendSummary(getReferenceIndex(), direction, localPosition - ciStart, localPosition + ciEnd,
					getEvidence());
		}
	}
	private EvidenceMetrics getEvidence() {
		EvidenceMetrics m = new EvidenceMetrics();
		for (VcfAttributes a : VcfAttributes.evidenceValues()) {
			m.set(a, getAttributeAsInt(a.attribute(), 0));
		}
		return m;
	}
	@Override
	public BreakendSummary getBreakendSummary() {
		if (location == null) throw new IllegalStateException(String.format("%s not a valid breakend", getID()));
		return location;
	}
	@Override
	public String getEvidenceID() {
		return getID();
	}
	@Override
	public byte[] getBreakpointSequence() {
		return breakpointSequence.getBytes(StandardCharsets.US_ASCII);
	}
	public String getBreakpointSequenceString() {
		if (breakpointSequence == null) throw new IllegalStateException(String.format("%s not a valid breakend", getID()));
		return breakpointSequence;
	}
	public String getAnchorSequenceString() {
		if (anchorSequence == null) throw new IllegalStateException(String.format("%s not a valid breakend", getID()));
		return anchorSequence;
	}
	@Override
	public byte[] getBreakpointQuality() {
		return breakpointQual;
	}
	@Override
	public boolean isValid() {
		return location != null;
	}
	public String getAssemblerProgram() { return getAttributeAsString(VcfAttributes.ASSEMBLY_PROGRAM.attribute(), null); }
	public String getAssemblyConsensus() { return getAttributeAsString(VcfAttributes.ASSEMBLY_CONSENSUS.attribute(), ""); }
	public double getAssemblyQuality() { return getAttributeAsDouble(VcfAttributes.ASSEMBLY_QUALITY.attribute(), 0); }
}