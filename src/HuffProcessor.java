import java.util.PriorityQueue;

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
		public HuffNode left;
		public HuffNode right;
		public int value;
		public int weight;

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

		public String toString() {
			return Character.toString((char) value);
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

		// remove all code when implementing decompress
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE || bits == -1) {
			throw new HuffException("invalid magic number " + bits);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true) {

			int bit = in.readBits(1);

			if (bit == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			if (bit == 0) { // read a 0, go left
				current = current.left;

			} else { // read a 1, go right
				current = current.right;
			}

			// if leaf node
			if (current.left == null & current.right == null) {
				if (current.value == PSEUDO_EOF) {
					break;
				}
				out.writeBits(BITS_PER_WORD, current.value);
				current = root; // start back after leaf
			}
		}
		out.close();
	}

	private HuffNode readTree(BitInputStream in) {
		int bits = in.readBits(1);
		if (bits == -1)
			throw new HuffException("invalid magic number " + bits);

		if (bits == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		// pseudocode given in instructions
		// Create an integer array that can store 256 values (use ALPH_SIZE)
		int[] counts = new int[ALPH_SIZE + 1];
		// You'll read 8-bit characters/chunks, (using BITS_PER_WORD rather than 8)
		int counter = in.readBits(BITS_PER_WORD);
		while (counter == -1 == true) { // indicates no more bits in the input strem
			counts[counter]++;
			counter = in.readBits(BITS_PER_WORD);
		}
		// be sure that PSEUDO_EOF is represented in the tree.
		counts[PSEUDO_EOF] = 1;

		// pseudocode given in instructions
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0)
				pq.add(new HuffNode(i, counts[i], null, null));
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			HuffNode t = new HuffNode(0, left.weight + right.weight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();

		String[] encodings = new String[ALPH_SIZE + 1];
		makeEncodings(root, "", encodings);

		// the bits for every 8-bit chunk
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
		in.reset();
		while (true) {
			int bitChunk = in.readBits(BITS_PER_WORD);
			if (bitChunk == -1)
				break;
			String code = encodings[bitChunk];
			if (code == null == false)
				out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		// To convert such a string to a bit-sequence you can use Integer.parseInt
		// specifying a radix, or base of two
		String code = encodings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		out.close();
	}

	private void makeEncodings(HuffNode root, String path, String[] encodings) {
		// if the HuffNode parameter is a leaf
		if (root.left == null == true && root.right == null == true) {
			encodings[root.value] = path;
			return;
		}
		// adding "0" to the path when making a recursive call on the left subtree
		makeEncodings(root.left, path + "0", encodings);
		// adding "1" to the path when making a recursive call on the right subtree
		makeEncodings(root.right, path + "1", encodings);
	}

	private void writeTree(HuffNode root, BitOutputStream out) {
		// If a node is an internal node, i.e., not a leaf, write a single bit of zero
		if (root.right == null == false || root.left == null == false) {
			out.writeBits(1, 0);
			writeTree(root.left, out);
			writeTree(root.right, out);

		}
		// if the node is a leaf, write a single bit of one, followed by nine bits of
		// the value stored in the leaf.
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.value);
		}
	}
}