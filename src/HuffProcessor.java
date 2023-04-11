import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 *
 *         Revise
 */

public class HuffProcessor {
	private class HuffNode implements Comparable<HuffNode> {
		HuffNode left;
		HuffNode right;
		int value;
		int weight;

		public HuffNode(int val, int count) {
			value = val;
			weight = count;
		}

		public HuffNode(int val, int count, HuffNode ltree, HuffNode rtree) {
			value = val;
			weight = count;
			left = ltree;
			right = rtree;
		}

		public int compareTo(HuffNode o) {
			return weight - o.weight;
		}
	}

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private boolean myDebugging = false;

	public HuffProcessor() {
		this(false);
	}

	public HuffProcessor(boolean debug) {
		myDebugging = debug;
	}

	private int[] getCounts(BitInputStream in) {
		// Create an integer array that can store 256 values (use ALPH_SIZE). You'll
		// read 8-bit characters/chunks, (using BITS_PER_WORD rather than 8)
		int[] counter = new int[ALPH_SIZE];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				break;
			}
			counter[val]++;
		}
		return counter;
	}

	private HuffNode makeTree(int[] counts) {
		// You'll use a greedy algorithm and a PriorityQueue of HuffNode objects to
		// create the trie.
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}

		pq.add(new HuffNode(PSEUDO_EOF, 1, null, null));
		// ***be sure that PSEUDO_EOF is represented in the tree. ***

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.weight + right.weight, left, right);
			pq.add(t);
		}

		HuffNode root = pq.remove();
		return root;

	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */

	private HuffNode readTree(BitInputStream in) {
		// Reading the tree using a helper method is required since reading the tree,
		// stored using a pre-order traversal, is much simpler with recursion.
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("Error: can't read bits");
		}
		if (bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}

	private void makeEncoding(HuffNode root, String path, String[] encodings) {
		// this method populates an array of Strings such that encodings[val] is the
		// encoding of the 8-bit chunk val
		if (root.left == null == true && root.right == null == true) {
			encodings[root.value] = path;
			return;
		} else {
			// add a single "0" for left-call and a single "1" for right-call
			makeEncoding(root.left, path + "0", encodings);
			makeEncoding(root.right, path + "1", encodings);
		}
	}

	private void writeTree(HuffNode root, BitOutputStream out) {
		// If a node is an internal node, i.e., not a leaf, write a single bit of zero.
		// Else, if the node is a leaf, write a single bit of one, followed by nine bits
		// of the value stored in the leaf.
		if (root.left == null && root.right == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.value);
		} else {
			out.writeBits(1, 0);
			writeTree(root.left, out);
			writeTree(root.right, out);
		}

	}

	public void compress(BitInputStream in, BitOutputStream out) {
		int[] freqs = getCounts(in);
		HuffNode root = makeTree(freqs);
		in.reset();
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
		String[] encodings = new String[ALPH_SIZE + 1];
		makeEncoding(root, "", encodings);
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				break;
			}
			String encoding = encodings[val];
			out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
		}

		String encoding = encodings[PSEUDO_EOF];
		// To convert such a string to a bit-sequence you can use Integer.parseInt
		// specifying a radix, or base of two.
		out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
		out.close();
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("invalid number:" + bits);
		}

		HuffNode root = readTree(in);
		HuffNode current = root;

		while (true) {
			int val = in.readBits(1);
			if (val == -1) {
				throw new HuffException("invalid magic number ");
			} else {
				if (val == 0) {
					current = current.left;
				} else {
					current = current.right;
				}
				if (current.left == null && current.right == null) {
					if (current.value == PSEUDO_EOF) {
						break;
					} else {
						out.writeBits(BITS_PER_WORD, current.value);
						current = root;
					}
				}
			}
		}
		out.close();
	}
}