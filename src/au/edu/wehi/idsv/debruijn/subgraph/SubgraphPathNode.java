package au.edu.wehi.idsv.debruijn.subgraph;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import au.edu.wehi.idsv.debruijn.DeBruijnGraphBase;
import au.edu.wehi.idsv.debruijn.PathNode;

public class SubgraphPathNode extends PathNode<DeBruijnSubgraphNode> {
	private int refCount = 0;
	public SubgraphPathNode(Iterable<Long> path, DeBruijnGraphBase<DeBruijnSubgraphNode> graph) {
		super(path, graph);
	}
	public SubgraphPathNode(SubgraphPathNode unsplit, int startIndex, int length, DeBruijnGraphBase<DeBruijnSubgraphNode> graph) {
		super(unsplit, startIndex, length, graph);
	}
	public SubgraphPathNode(DeBruijnGraphBase<DeBruijnSubgraphNode> graph, Iterable<SubgraphPathNode> path) {
		super(graph, path);
	}
	@Override
	protected void onKmersChanged(DeBruijnGraphBase<DeBruijnSubgraphNode> graph) {
		refCount = 0; 
		super.onKmersChanged(graph);
		for (long kmer : getPath()) {
			if (graph.getKmer(kmer).isReference()) refCount++;
		}
	}
	public boolean containsReferenceKmer() { return refCount > 0; }
	public boolean containsNonReferenceKmer() { return refCount < length(); }
}
