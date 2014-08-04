package au.edu.wehi.idsv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamPairUtil.PairOrientation;
import htsjdk.samtools.fastq.FastqRecord;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import au.edu.wehi.idsv.metrics.RelevantMetrics;

import com.google.common.collect.Lists;

public class SAMEvidenceSourceTest extends IntermediateFilesTest {
	@Test
	public void should_generate_rp_sc_mate_fq() {
		createInput();
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(0, getRP(source).size());
		assertEquals(0, getSC(source).size());
		assertEquals(0, getMate(source).size());
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void should_generate_rp_sc_mate_fq_per_chr() {
		createInput();
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(0, new PerChr().getRP(source).size());
		assertEquals(0, new PerChr().getSC(source).size());
		assertEquals(0, new PerChr().getMate(source).size());
		assertEquals(0, new PerChr().getFastqRecords(source).size());
	}
	@Test
	public void sc_should_be_located_in_sc_bam_for_chr() {
		createInput(Read(1, 1, "50M50S"));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(1, new PerChr().getSC(source, "polyACGT").size());
	}
	@Test
	public void oea_should_be_located_in_rp_bam_for_chr() {
		createInput(OEA(1, 1, "100M", true));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(1, new PerChr().getRP(source, "polyACGT").size());
	}
	@Test
	public void dp_should_be_located_in_rp_bam_for_chr() {
		createInput(DP(1, 1, "100M", true, 2, 2, "100M", true));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(1, new PerChr().getRP(source, "polyACGT").size());
		assertEquals(1, new PerChr().getRP(source, "random").size());
	}
	@Test
	public void concordant_read_should_not_be_located_in_rp_or_scbam() {
		createInput(Read(1, 1, "100M"));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(0, getRP(source).size());
		assertEquals(0, getSC(source).size());
	}
	@Test
	public void oea_mate_should_be_located_in_mate_bam_for_chr() {
		createInput(OEA(1, 1, "100M", true));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<SAMRecord> rs = new PerChr().getMate(source, "polyACGT");
		assertEquals(1, rs.size());
		SAMRecord mate = rs.get(0);
		assertTrue(mate.getReadUnmappedFlag());
		assertEquals(1, (int)mate.getMateReferenceIndex());
		assertEquals(1, mate.getMateAlignmentStart());
	}
	@Test
	public void dp_mate_should_be_located_in_mate_bam_for_chr() {
		createInput(DP(1, 1, "100M", true, 2, 2, "100M", true));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(1, new PerChr().getMate(source, "polyACGT").size());
		assertEquals(1, new PerChr().getMate(source, "random").size());
		SAMRecord mate = new PerChr().getRP(source, "polyACGT").get(0);
		assertFalse(mate.getReadUnmappedFlag());
		assertEquals(2, mate.getMateAlignmentStart());
		assertEquals(2, mate.getMateAlignmentStart());
	}
	@Test
	public void mate_should_be_sorted_by_mate_coordinate() {
		createInput(DP(1, 1, "100M", true, 2, 5, "100M", true),
		   DP(1, 2, "100M", true, 2, 4, "100M", true),
		   DP(1, 3, "100M", true, 2, 6, "100M", true),
		   OEA(1, 4, "100M", false));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<SAMRecord> rs =  getMate(source);
		// polyACGT
		assertEquals(7, rs.size());
		assertEquals(1, rs.get(0).getMateAlignmentStart());
		assertEquals(2, rs.get(1).getMateAlignmentStart());
		assertEquals(3, rs.get(2).getMateAlignmentStart());
		assertEquals(4, rs.get(3).getMateAlignmentStart());
		// random 
		assertEquals(4, rs.get(4).getMateAlignmentStart());
		assertEquals(5, rs.get(5).getMateAlignmentStart());
		assertEquals(6, rs.get(6).getMateAlignmentStart());
	}
	@Test
	public void should_create_metrics() {
		createInput(RP(1, 1, 100, 10));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertTrue(getCommandlineContext().getFileSystemContext().getIdsvMetrics(input).exists());
		assertTrue(getCommandlineContext().getFileSystemContext().getInsertSizeMetrics(input).exists());
	}
	@Test
	public void should_process_metrics() {
		createInput(RP(0, 1, 2, 1), RP(0, 1, 7, 5));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		RelevantMetrics metrics = source.getMetrics();
		assertEquals(5, metrics.getMaxReadLength());
		// 12345678901234567890
		// ----> <----
		assertEquals(11, metrics.getMaxFragmentSize());
		assertEquals(PairOrientation.FR, metrics.getPairOrientation());
	}
	@Test
	public void should_set_NM_tag() {
		createInput(withSequence(S(POLY_A).substring(0, 100), Read(0, 1, "50M50S")));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(0, (int)getSC(source).get(0).getIntegerAttribute("NM"));
	}
	@Test
	public void should_write_long_sc_to_fastq() {
		createInput(ValidSC());
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		List<FastqRecord> fastq = getFastqRecords(source);
		assertEquals(2, fastq.size());
		assertTrue(fastq.get(0).getReadHeader().contains("SC1"));
		assertTrue(fastq.get(1).getReadHeader().contains("SC1"));
		assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAA", fastq.get(0).getReadString());
		// +33 encoding of mapping quality 5
		assertEquals("&&&&&&&&&&&&&&&&&&&&&&&&&", fastq.get(0).getBaseQualityString());
	}
	@Test
	public void realign_min_mapq_should_filter_sc() {
		createInput(ValidSC());
		ProcessingContext pc = getCommandlineContext();
		pc.getSoftClipParameters().minReadMapq = 6;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void realign_long_sc_length_should_filter_sc() {
		createInput(ValidSC());
		ProcessingContext pc = getCommandlineContext();
		pc.getRealignmentParameters().minLength = 26;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void realign_min_id_should_filter_sc() {
		SAMRecord r = ValidSC();
		byte[] readBases = r.getReadBases();
		for (int i = 25; i < 51; i++) {
			// change just over half the bases from A to T
			readBases[i] = 'T';
		}
		r.setReadBases(readBases);
		createInput(RP(0, 1, 2, 1));
		ProcessingContext pc = getCommandlineContext();
		pc.getSoftClipParameters().minAnchorIdentity = 50;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void realign_min_qual_should_filter_sc() {
		createInput(ValidSC());
		ProcessingContext pc = getCommandlineContext();
		pc.getRealignmentParameters().minAverageQual = 6;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void short_sc_should_not_be_considered_evidence() {
		createInput(Read(1, 1, "50M50S"));
		ProcessingContext pc = getCommandlineContext();
		pc.getSoftClipParameters().minLength = 51;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(0, getSC(source).size());
	}
	@Test
	public void min_qual_should_only_consider_sc_qualities() {
		SAMRecord r = ValidSC();
		byte[] qual = r.getBaseQualities();
		for (int i = 25; i < 75; i++) {
			qual[i] = 40;
		}
		r.setBaseQualities(qual);
		createInput(r);
		ProcessingContext pc = getCommandlineContext();
		pc.getRealignmentParameters().minAverageQual = 6;
		SAMEvidenceSource source = new SAMEvidenceSource(pc, input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		
		assertEquals(0, getFastqRecords(source).size());
	}
	@Test
	public void per_chr_iterator_should_return_all_evidence() {
		createInput(new SAMRecord[] { Read(1, 1, "50M50S") },
				RP(0, 200, 100), // max frag size
				DP(1, 1, "100M", true, 2, 5, "100M", true),
			   DP(1, 2, "100M", true, 2, 4, "100M", true),
			   DP(1, 3, "100M", true, 2, 6, "100M", true),
			   OEA(1, 4, "100M", false));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator());
		assertEquals(8, list.size()); // 1 SC + 3 * 2 DP + 1 OEA
	}
	@Test
	public void per_chr_iterator_should_iterator_over_chr_in_dictionary_order() {
		createInput(
				Read(0, 1, "50M50S"),
				Read(1, 1, "50M50S"),
				Read(2, 1, "50M50S"),
				Read(3, 1, "50M50S"),
				Read(4, 1, "50M50S")
				);
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator());
		for (int i = 0; i <= 4; i++) {
			assertEquals(i, list.get(i).getBreakendSummary().referenceIndex);
		}
	}
	@Test
	public void per_chr_iterator_chr_should_return_only_evidence() {
		createInput(
				Read(0, 1, "50M50S"),
				Read(1, 1, "50M50S"),
				Read(2, 1, "50M50S"),
				Read(3, 1, "50M50S"),
				Read(4, 1, "50M50S")
				);
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(true), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator("polyACGT"));
		assertEquals(1, list.size());
		assertEquals(1, list.get(0).getBreakendSummary().referenceIndex);
	}
	@Test
	public void iterator_should_return_all_evidence() {
		createInput(new SAMRecord[] { Read(1, 1, "50M50S") },
				RP(0, 100, 200, 100),
				DP(1, 1, "100M", true, 2, 5, "100M", true),
			   DP(1, 2, "100M", true, 2, 4, "100M", true),
			   DP(1, 3, "100M", true, 2, 6, "100M", true),
			   OEA(1, 4, "100M", false));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator());
		assertEquals(8, list.size()); // 1 SC + 3 * 2 DP + 1 OEA
	}
	@Test
	public void iterator_should_iterator_over_chr_in_dictionary_order() {
		createInput(
				Read(0, 1, "50M50S"),
				Read(1, 1, "50M50S"),
				Read(2, 1, "50M50S"),
				Read(3, 1, "50M50S"),
				Read(4, 1, "50M50S")
				);
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator());
		for (int i = 0; i <= 4; i++) {
			assertEquals(i, list.get(i).getBreakendSummary().referenceIndex);
		}
	}
	@Test
	public void iterator_chr_should_return_only_evidence() {
		createInput(
				Read(0, 1, "50M50S"),
				Read(1, 1, "50M50S"),
				Read(2, 1, "50M50S"),
				Read(3, 1, "50M50S"),
				Read(4, 1, "50M50S")
				);
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator("polyACGT"));
		assertEquals(1, list.size());
		assertEquals(1, list.get(0).getBreakendSummary().referenceIndex);
	}
	@Test
	public void should_set_evidence_source_to_self() {
		createInput(Read(0, 1, "50M50S"));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> list = Lists.newArrayList(source.iterator());
		assertEquals(1, list.size());
		assertEquals(source, list.get(0).getEvidenceSource());
	}
	@Test
	public void should_default_fragment_size_to_read_length_for_unpaired_reads() {
		createInput(Read(0, 1, "50M50S"));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		assertEquals(100, source.getMetrics().getMaxFragmentSize());
	}
	@Test
	public void iterator_evidence_should_be_sorted_by_evidence_natural_ordering() {
		createInput(new SAMRecord[] { Read(1, 1, "50M50S") },
				new SAMRecord[] { Read(1, 1, "50S50M") },
				RP(0, 100, 200, 100),
				DP(1, 1, "100M", true, 2, 5, "100M", true),
			   DP(1, 2, "100M", false, 2, 4, "100M", true),
			   DP(1, 3, "100M", true, 2, 6, "100M", true),
			   OEA(1, 4, "100M", false),
			   OEA(1, 4, "100M", true));
		SAMEvidenceSource source = new SAMEvidenceSource(getCommandlineContext(), input, false);
		source.completeSteps(ProcessStep.ALL_STEPS);
		List<DirectedEvidence> result = Lists.newArrayList(source.iterator());
		List<DirectedEvidence> sorted = Lists.newArrayList(result);
		Collections.sort(sorted, DirectedEvidenceOrder.ByNatural);
		assertEquals(sorted, result);
	}
}