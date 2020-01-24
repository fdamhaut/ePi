package gui;

import guiOptions.OptionsComp;
import guiOptions.OptionsMCTS;
import guiOptions.OptionsMinimax;
import guiOptions.OptionsMultiTrain;
import guiOptions.OptionsTD;
import guiOptions.OptionsValueFunc;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import agentIO.LoadSaveTD;
import mcts.MCTSParams;
import mcts.MCTSPlayer;
import nTupleTD.TDParams;
import nTupleTD.TDSAgent;
import nTupleTD.ValueFuncC4;
import nTupleTD.TDParams.GameInterval;
import nTupleTD.TDParams.UpdateMethod;

import competition.Evaluate;
import competition.ResultBestMoveMulti;
import competition.Competition;
import competition.ResultCompMulti;
import competition.Progress;
import competition.ResultCompSingle;
import competition.ResultTrainMulti;
import competition.ResultValueFuncMulti;
import competition.TDSMultiTrain;
import competition.ValueFuncTest;

import openingBook.BookSum;

import c4.AlphaBetaAgent;
import c4.AlphaBetaTDSAgent;
import c4.ConnectFour;
import c4.MoveList;
import c4.Agent;

/**
 * Contains the "Logic" of the GUI
 * 
 * @author Markus Thill
 * 
 */
public class C4Game extends JPanel implements Runnable, ListOperation {

	private static final long serialVersionUID = -59357720973949917L;

	// the game buttons and text fields on the right of the window
	public C4Buttons c4Buttons;

	public C4Menu c4Menu;
	protected C4Frame_v2_14 c4Frame;
	private Thread playThread = null;

	// The Panel including the board and value-bar on the left of the window
	private JPanel boardPanel;

	// The 42 fields of the connect-four board
	private JPanel playingBoardPanel;
	private ImgShowComponent playingBoard[][];

	// The value-panel
	private JLabel[][] valueBoard;

	// Needed, for performing standard connect-four operations (finding
	// win-rows, check if legal move, etc.)
	protected ConnectFour c4 = new ConnectFour();

	// All moves during a game are stored in a movelist, so that moves can be
	// taken back or repeated again
	private MoveList mvList = new MoveList();

	// Because the process creates parallel running threads, sometime a
	// semaphore is needed to guarantee exclusive access on some objects
	private Semaphore mutex = new Semaphore(1);

	// Labels for the Values
	JLabel lValueTitle;
	JLabel lValueGTV;
	JLabel lValueAgent;
	JLabel lValueEval;

	// The opening-books are loaded only once to save memory. All agents, that
	// need them, use the same books.
	private final BookSum books = new BookSum();

	// Possible states
	protected enum State {
		TRAIN_X, TRAIN_O, TRAIN_EVAL, PLAY, COMPETE, MULTICOMPETE, IDLE, SETBOARD, TESTVALUEFUNC, TESTBESTMOVE, SHOWNTUPLE /* unused */, SETNTUPLE, INSPNTUPLE, MULTITRAIN, EVALUATE, SAVE_X, SAVE_O, SAVE_EVAL, LOAD_X, LOAD_O, LOAD_EVAL, SAVE_WEIGHTS_X, SAVE_WEIGHTS_O, SAVE_WEIGHTS_EVAL, LOAD_WEIGHTS_X, LOAD_WEIGHTS_O, LOAD_WEIGHTS_EVAL
	};

	protected State state = State.IDLE;

	// Possible Actions
	protected enum Action {
		NOACTION, MOVEBACK, NEXTMOVE, DELETE, CHANGE
	};

	protected Action action = Action.NOACTION;

	// Standard Alpha-Beta-Agent
	public AlphaBetaAgent alphaBetaStd = null;

	// Players
	protected Agent[] players = new Agent[3];
	private int curPlayer;

	// Agent for the game-theoretic-values (perfect minimax-agent)
	private Agent GTVab = null;

	// Flag that is set, when a game is won by a player or drawn.
	private boolean gameOver = false;

	// Show gameTheoretic Values
	protected boolean showGTV = false;
	protected boolean showAgentV = false;
	protected boolean showAgentEvalV = false;

	// Competition
	private Competition comp;

	// Progress for some Functions
	private Progress progress = null;

	// Other Windows
	protected OptionsMinimax winOptionsGTV = new OptionsMinimax(
			AlphaBetaAgent.TRANSPOSBYTES);
	protected OptionsComp winCompOptions = new OptionsComp();
	protected ResultOverview winCompResult = new ResultOverview();
	protected OptionsValueFunc winValueFuncOptions = new OptionsValueFunc();
	// Not needed anymore: protected ShowNTuples winNTupleWindow = new
	// ShowNTuples(this);
	protected ShowTupleList winConfigTuples = new ShowTupleList(this, c4Frame,
			this, "N-Tuple Configuration");
	protected LUTShow winLUTShow = new LUTShow(this);
	protected OptionsMultiTrain winMultiTrainOptions = new OptionsMultiTrain();

	// Options-Windows for the current agent-type
	// params[0]:Player X
	// params[1]:Player O
	// params[2]:Player Eval
	protected final JFrame params[] = new JFrame[3];

	// For Setting N-Tuples manually
	protected ArrayList<ArrayList<Integer>> nTupleList = new ArrayList<ArrayList<Integer>>();

	// Evaluation for TD-Agents
	Evaluate eval;

	// If the status-message was changed, this flag will be set, to indicate,
	// that the statusbar has to be updated
	protected boolean syncStatusBar = false;

	// Flag, that is set, when the Step-Button is selected. This causes the
	// current agent to make a move
	private boolean playStep = false;

	// Saving/Loading of Agents is done with this
	private LoadSaveTD tdAgentIO;

	public C4Game() {
		initGame();
	}

	public C4Game(C4Frame_v2_14 frame) {
		c4Frame = frame;
		initGame();
	}

	public void init() {
		// Start a thread
		playThread = new Thread(this);
		playThread.start();
	}

	private void initGame() {
		playingBoard = new ImgShowComponent[7][6];
		valueBoard = new JLabel[3][7];
		c4Buttons = new C4Buttons(this);
		c4Menu = new C4Menu(this, c4Frame);

		tdAgentIO = new LoadSaveTD(this, c4Buttons, c4Frame);

		playingBoardPanel = initPlayingBoard();

		lValueTitle = new JLabel("Overall Result of the Value-Function");
		lValueTitle.setFont(new Font("Times New Roman", 1, 18));
		lValueGTV = new JLabel("Hallo");
		lValueAgent = new JLabel("Hallo");
		lValueEval = new JLabel("Hallo");

		lValueGTV.setToolTipText("Value for the game-theoretic value");
		lValueAgent.setToolTipText("Value for the selected Agent");
		lValueEval.setToolTipText("Value for the Evaluation");

		JLabel Title;
		Title = new JLabel(C4Frame_v2_14.TITLE + " " + C4Frame_v2_14.VERSION,
				JLabel.CENTER);
		Title.setForeground(Color.black);
		Font font = new Font("Times New Roman", 1, 18);
		Title.setFont(font);

		setLayout(new BorderLayout(10, 10));
		setBackground(Color.white);
		boardPanel = new JPanel();
		boardPanel.add(playingBoardPanel);

		boardPanel.setLayout(new GridBagLayout());
		JPanel z = initValuePanel();
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;

		boardPanel.add(z, c);

		c.gridy++;
		boardPanel.add(lValueTitle, c);

		c.gridy++;
		boardPanel.add(lValueGTV, c);

		c.gridy++;
		boardPanel.add(lValueAgent, c);

		c.gridy++;
		boardPanel.add(lValueEval, c);

		add(c4Buttons, BorderLayout.EAST);
		add(boardPanel, BorderLayout.CENTER);
		add(Title, BorderLayout.NORTH);

		changeState(State.IDLE);

		printCurAgents();

		// Init the Standard Alpha-Beta-Agent
		// Until yet, there were no changes of the Options
		OptionsMinimax min = winOptionsGTV;
		alphaBetaStd = new AlphaBetaAgent(books);
		alphaBetaStd.resetBoard();
		alphaBetaStd.setTransPosSize(min.getTableIndex());
		alphaBetaStd.setBooks(min.useNormalBook(), min.useDeepBook(),
				min.useDeepBookDist());
		alphaBetaStd.setDifficulty(min.getSearchDepth());
		alphaBetaStd.randomizeEqualMoves(min.randomizeEqualMoves());

		// Init evaluation
		eval = new Evaluate(alphaBetaStd);
	}

	private JPanel initValuePanel() {
		JPanel z = new JPanel();
		z.setLayout(new GridLayout(1, 7, 2, 2));
		z.setBackground(Color.black);
		for (int i = 0; i < 7; i++) {
			JPanel vb = new JPanel();
			vb.setLayout(new GridLayout(3, 0, 2, 2));
			vb.setBackground(Color.orange);
			for (int j = 0; j < 3; j++) {
				valueBoard[j][i] = new JLabel("0.0", JLabel.CENTER);
				valueBoard[j][i].setBackground(Color.orange);
				vb.add(valueBoard[j][i]);
			}
			valueBoard[0][i].setToolTipText("Values for the single columns "
					+ "of the current board (game-theoretic value)");
			valueBoard[1][i].setToolTipText("Values for the single columns "
					+ "of the current board (selected Agent)");
			valueBoard[2][i].setToolTipText("Values for the single columns "
					+ "of the current board (Evaluation)");
			z.add(vb);
		}
		return z;
	}

	private JPanel initPlayingBoard() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(6, 7, 2, 2));
		panel.setBackground(Color.BLACK);

		for (int i = 0; i < 42; i++)
			panel.add(new Canvas());

		// Add Playing Field
		for (int i = 0; i < 7; i++) {
			for (int j = 0; j < 6; j++) {
				playingBoard[i][j] = replaceImage(playingBoard[i][j],
						ImgShowComponent.EMPTY0, i, j);
				panel.remove((5 - j) * 7 + i);
				panel.add(playingBoard[i][j], (5 - j) * 7 + i);
			}
		}

		return panel;
	}

	private ImgShowComponent replaceImage(ImgShowComponent oldImg,
			int imgIndex, int num1, int num2) {
		ImgShowComponent imgShComp = ImgShowComponent.replaceImg(oldImg,
				imgIndex);
		if (oldImg != imgShComp)
			imgShComp.addMouseListener(new MouseHandler(num1, num2) {
				public void mouseClicked(MouseEvent e) {
					handleMouseClick(x, y);
				}
			});
		return imgShComp;
	}

	private boolean makeCompleteMove(int x, int player, String sPlayer) {
		if (!gameOver && c4.getColHeight(x) != 6) {
			checkWin(x, sPlayer);
			setPiece(x);
			swapPlayer();
			if (c4.isDraw() && !gameOver) {
				int col = mvList.readPrevMove();
				if (!c4.canWin(2, col, c4.getColHeight(col) - 1)) {
					gameOver = true;
					new MessageBox("Draw!!!       ", "Game Over");
				}
			}
			if (!gameOver)
				printValueBar();
			return true;
		}
		return false; // error
	}

	protected boolean makeCompleteMove(int x, String sPlayer) {
		return makeCompleteMove(x, curPlayer, sPlayer);
	}

	private void setPiece(int x, int player) {
		putPiece(x, player);
		int last = mvList.readPrevMove();
		if (last != -1) {
			int i = last;
			int j = c4.getColHeight(i) - 1;
			if (x == i)
				j--;
			unMarkMove(i, j, player);
		}

		mvList.putMove(x);
	}

	private void setPiece(int x) {
		setPiece(x, curPlayer);
	}

	private void putPiece(int x, int player) {
		int y = c4.getColHeight(x);
		c4.putPiece(player + 1, x);
		markMove(x, y, player);
	}

	private void markMove(int x, int y, int player) {
		int imgIndex = (player == 0 ? ImgShowComponent.YELLOW_M
				: ImgShowComponent.RED_M);
		ImgShowComponent newImg = replaceImage(playingBoard[x][y], imgIndex, x,
				y);

		if (newImg != playingBoard[x][y]) {
			playingBoardPanel.remove((5 - y) * 7 + x);
			playingBoard[x][y] = newImg;
			playingBoardPanel.add(playingBoard[x][y], (5 - y) * 7 + x);
			playingBoardPanel.invalidate();
			playingBoardPanel.validate();
		}
	}

	private void unMarkMove(int x, int y, int player) {
		int imgIndex = (player == 0 ? ImgShowComponent.RED
				: ImgShowComponent.YELLOW);
		ImgShowComponent newImg = replaceImage(playingBoard[x][y], imgIndex, x,
				y);
		if (newImg != playingBoard[x][y]) {
			playingBoardPanel.remove((5 - y) * 7 + x);
			playingBoard[x][y] = newImg;
			playingBoardPanel.add(playingBoard[x][y], (5 - y) * 7 + x);
			playingBoardPanel.invalidate();
			playingBoardPanel.validate();
		}
	}

	private void removePiece(int x, int player) {
		int y = c4.getColHeight(x) - 1;
		c4.removePiece(player + 1, x);
		removePiece(x, y, player);
	}

	private void removePiece(int x, int y, int player) {
		int imgIndex = ImgShowComponent.EMPTY0;
		ImgShowComponent newImg = replaceImage(playingBoard[x][y], imgIndex, x,
				y);
		if (newImg != playingBoard[x][y]) {
			playingBoardPanel.remove((5 - y) * 7 + x);
			playingBoard[x][y] = newImg;
			playingBoardPanel.add(playingBoard[x][y], (5 - y) * 7 + x);
			playingBoardPanel.invalidate();
			playingBoardPanel.validate();
		}
	}

	private void putPiece(int x, int y, int player) {
		int imgIndex = (player == 0 ? ImgShowComponent.YELLOW
				: ImgShowComponent.RED);
		ImgShowComponent newImg = replaceImage(playingBoard[x][y], imgIndex, x,
				y);
		if (newImg != playingBoard[x][y]) {
			playingBoardPanel.remove((5 - y) * 7 + x);
			playingBoard[x][y] = newImg;
			playingBoardPanel.add(playingBoard[x][y], (5 - y) * 7 + x);
			playingBoardPanel.invalidate();
			playingBoardPanel.validate();
		}
	}

	protected void resetBoard() {
		for (int i = 0; i < 7; i++) {
			for (int j = 0; j < 6; j++) {
				int imgIndex = ImgShowComponent.EMPTY0;
				ImgShowComponent newImg = replaceImage(playingBoard[i][j],
						imgIndex, i, j);
				if (newImg != playingBoard[i][j]) {
					playingBoardPanel.remove((5 - j) * 7 + i);
					playingBoard[i][j] = newImg;
					playingBoardPanel.add(playingBoard[i][j], (5 - j) * 7 + i);
					playingBoardPanel.invalidate();
					playingBoardPanel.validate();
				}
			}
		}
		c4.resetBoard();
		mvList.reset();
		gameOver = false;
		curPlayer = 0;
	}

	private void checkWin(int x, String sPlayer) {
		checkWin(curPlayer, x, sPlayer);
	}

	private void checkWin(int player, int x, String sPlayer) {
		if (c4.canWin(player + 1, x)) {
			if (sPlayer != null)
				new MessageBox(sPlayer + " Win!!       ", "Game Over");
			else
				new MessageBox("Game Over!!!", "Game Over");
			gameOver = true;
		}
	}

	private void swapPlayer() {
		curPlayer = (1 - curPlayer);
	}

	protected void setInitialBoard() {
		mvList.reset();
		resetBoard();
		curPlayer = 0;
	}

	private void initPlay() {
		curPlayer = c4.countPieces() % 2;
		gameOver = false;
	}

	@SuppressWarnings("unused")
	private void printColValues(Agent pa) {
		double[] vals = pa.getNextVTable(c4.getBoard(), false);
		for (int i = 0; i < vals.length; i++)
			valueBoard[0][i].setText(vals[i] + "");
	}

	private double[] getGTV() {
		if (GTVab == null)
			GTVab = initGTVAgent();
		double[] vals = GTVab.getNextVTable(c4.getBoard(), true);
		return vals;
	}

	private double getSingleGTV() {
		if (GTVab == null)
			GTVab = initGTVAgent();
		return GTVab.getScore(c4.getBoard(), true);
	}

	protected void printValueBar() {
		new Thread("") {
			public void run() {
				double[] realVals = new double[7];
				double[] agentvals = new double[7];
				double[] evalVals = new double[7];

				// Reset Labels
				lValueAgent.setText("Agent: ");
				lValueEval.setText("Eval: ");
				lValueGTV.setText("GTV: ");

				Agent pa = null;
				if (showAgentV)
					pa = players[curPlayer];

				boolean useSigmoid = false;

				if (pa != null) {
					agentvals = pa.getNextVTable(c4.getBoard(), false);
					int val = (int) (pa.getScore(c4.getBoard(), true) * 100);
					lValueAgent.setText("Agent:     " + val);
				}

				if (agentvals != null)
					for (int i = 0; i < agentvals.length; i++)
						if (Math.abs(agentvals[i]) > 1.0) {
							useSigmoid = true;
							break;
						}

				if (useSigmoid && agentvals != null)
					for (int i = 0; i < agentvals.length; i++)
						agentvals[i] = Math.tanh(agentvals[i]);
				if (showGTV) {
					realVals = getGTV();
					String valGTV = "GTV:        "
							+ (int) (getSingleGTV() * 100) + "";
					lValueGTV.setText(valGTV);
					lValueGTV.setPreferredSize(getMaximumSize());
				}
				if (showAgentEvalV && players[2] != null) {
					evalVals = players[2].getNextVTable(c4.getBoard(), true);
					int val = (int) (players[2].getScore(c4.getBoard(), true) * 100);
					lValueEval.setText("Eval:        " + val + "");
				}
				for (int i = 0; i < 7; i++) {
					if (realVals != null)
						valueBoard[0][i]
								.setText((int) (realVals[i] * 100) + "");
					if (agentvals != null)
						valueBoard[1][i].setText((int) (agentvals[i] * 100)
								+ "");
					if (evalVals != null)
						valueBoard[2][i]
								.setText((int) (evalVals[i] * 100) + "");
				}
				System.gc();
			}
		}.start();
	}

	private void printCurAgents() {
		new Thread("") {
			public void run() {
				int x = 0;
				while (true) {
					c4Buttons.printCurAgents(players);
					if (syncStatusBar) {
						x++;
						if (x == 7) {
							syncStatusBar = false;
							x = 0;
						}
					}
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (progress != null) {
						c4Buttons.setProgressBar(progress.getProgress());
						c4Buttons.statusBar.setMessage(progress
								.getStatusMessage());
					} else if (!syncStatusBar) {
						c4Buttons.setProgressBar(0);
					}
				}
			}
		}.start();
	}

	private Agent initGTVAgent() {
		if (!winOptionsGTV.usePresetting()) {
			AlphaBetaAgent ab = new AlphaBetaAgent(books);
			ab.resetBoard();
			// New
			OptionsMinimax min = winOptionsGTV;
			ab.setTransPosSize(min.getTableIndex());
			ab.setBooks(min.useNormalBook(), min.useDeepBook(),
					min.useDeepBookDist());
			ab.setDifficulty(min.getSearchDepth());
			ab.randomizeEqualMoves(min.randomizeEqualMoves());
			return ab;
		}
		return alphaBetaStd;
	}

	protected Agent initAlphaBetaAgent(int player) {
		if (params[player] == null
				|| !params[player].getClass().equals(OptionsMinimax.class))
			params[player] = new OptionsMinimax(AlphaBetaAgent.TRANSPOSBYTES);
		OptionsMinimax min = (OptionsMinimax) params[player];
		if (!min.usePresetting()) {
			AlphaBetaAgent ab = new AlphaBetaAgent(books);
			ab.resetBoard();

			ab.setTransPosSize(min.getTableIndex());
			ab.setBooks(min.useNormalBook(), min.useDeepBook(),
					min.useDeepBookDist());
			ab.setDifficulty(min.getSearchDepth());
			ab.randomizeEqualMoves(min.randomizeEqualMoves());

			// Using N-Tuple-System for move-Ordering
			// ab.setTDAgent((TDSAgent) players[2]);
			return ab;
		}
		return alphaBetaStd;
	}

	/**
	 * Compare Time from normal move-ordering with time from
	 * n-Tuple-move-ordering of the Alpha-Beta-Agent. Is done for all boards
	 * with 0,1,2 stones.
	 */
	@SuppressWarnings("unused")
	private void compareTime() {
		int boards[][][] = eval.getBoards();
		long time[][] = new long[2][boards.length];
		long zstStart, zstStop;
		AlphaBetaAgent ab;

		for (int i = 0; i < boards.length; i++) {
			// N-Tuple-System for Move ordering
			System.out.println(i + ":");
			ab = (AlphaBetaAgent) initAlphaBetaAgent(0);
			ab.setTDAgent((TDSAgent) players[2]);
			zstStart = System.currentTimeMillis();
			ab.getBestMove(boards[i]);
			zstStop = System.currentTimeMillis();
			time[0][i] = zstStop - zstStart;
			System.out.println("time1: " + time[0][i]);

			// Normal Minimax
			ab = (AlphaBetaAgent) initAlphaBetaAgent(1);
			zstStart = System.currentTimeMillis();
			ab.getBestMove(boards[i]);
			zstStop = System.currentTimeMillis();
			time[1][i] = zstStop - zstStart;
			System.out.println("time2: " + time[1][i]);

			c4Buttons.setProgressBar((i * 100) / boards.length);
		}
		String ln1 = Arrays.toString(time[0]);
		String ln2 = Arrays.toString(time[1]);
		System.out.println("start1" + ln1 + "end1");
		System.out.println("start2" + ln2 + "end2");
	}

	protected Agent initAlphaBetaTDSAgent(int player) {
		if (params[player] == null
				|| !params[player].getClass().equals(OptionsMinimax.class))
			params[player] = new OptionsMinimax(AlphaBetaAgent.TRANSPOSBYTES);
		OptionsMinimax min = (OptionsMinimax) params[player];

		// Evaluation must be a TDS-Player
		AlphaBetaTDSAgent abTDS = new AlphaBetaTDSAgent(books,
				(TDSAgent) players[2]);
		abTDS.resetBoard();

		abTDS.setTransPosSize(min.getTableIndex());
		abTDS.setBooks(min.useNormalBook(), min.useDeepBook(),
				min.useDeepBookDist());
		abTDS.setDifficulty(min.getSearchDepth());
		abTDS.randomizeEqualMoves(min.randomizeEqualMoves());

		return abTDS;
	}

	protected Agent initTDSAgent(int player) {
		TDParams tdPar;
		if (params[player] == null
				|| !params[player].getClass().equals(OptionsTD.class))
			params[player] = new OptionsTD(new TDParams());
		OptionsTD opTD = (OptionsTD) params[player];
		tdPar = opTD.getTDParams();
		AlphaBetaAgent ab = null;
		if (tdPar.stopAfterMoves < 40)
			ab = alphaBetaStd;

		TDSAgent tds = new TDSAgent(tdPar, ab);

		// Set N-Tuples in the Options
		Integer nTuples[][] = tds.m_Net.getNTuples1Dim();
		opTD.setNTuples(nTuples);

		return tds;
	}

	protected Agent initMCTSAgent(int player) {
		if (params[player] == null
				|| !params[player].getClass().equals(OptionsMCTS.class))
			params[player] = new OptionsMCTS(new MCTSParams());
		OptionsMCTS opMCTS = (OptionsMCTS) params[player];
		MCTSPlayer mctsPlayer = new MCTSPlayer(opMCTS.getMCTSParams());

		return mctsPlayer;
	}

	public void trainTDSAgent(TDSAgent tds, int gameNum, int player) {
		long zstStart = System.currentTimeMillis();
		progress = tds;

		OptionsTD opTD = (OptionsTD) params[player];
		TDParams tdPar = opTD.getTDParams();
		tds.setTDParams(tdPar);

		boolean measureStrength = tdPar.singleMatch;
		// boolean evaluate = ((OptionsTD) params[player]).evaluate();
		// int interval = tdPar.infoInterval;

		// Init competition for Strength-measurement
		Competition comp = null;
		boolean getValues = false;
		int pieceNum = 0;
		if (measureStrength) {
			comp = new Competition(tds, alphaBetaStd, alphaBetaStd);
			pieceNum = tdPar.stopAfterMoves;
			getValues = false;
		}

		// Start training
		boolean useCSV = true;
		if (useCSV) {
			System.out.print("gameNr;alpha;epsilon;Score012;"
					+ "Score50Games;Draw50Games;");
			if (tdPar.updateMethod == UpdateMethod.IDBD_WK)
				System.out.println("k_acc;");
			System.out.print("\n");
		}

		DecimalFormat df = new DecimalFormat("+0.00000;-0.00000");
		long activeEligTracesOverall = 0;
		long activeEligTracesInterval = 0;
		for (int i = 0; i <= gameNum; i++) {
			tds.trainNet(1);
			if (true) {
				int activeTraces = tds.countActiveEligTraces();
				activeEligTracesOverall += activeTraces;
				activeEligTracesInterval += activeTraces;
			}
			if (GameInterval.evalNecessary(tdPar.infoInterval, i) && i != 0) {

				if (useCSV) {
					System.out.print(i + ";" + df.format(tds.getAlpha()) + ";"
							+ df.format(tds.getEpsilon()) + ";");

					// Get score
					double[] score = eval.getScore(tds, alphaBetaStd, tdPar);
					if (!tdPar.evaluate012)
						score[0] = Double.NaN;
					if (!tdPar.evaluateAgent)
						score[1] = score[2] = Double.NaN;

					System.out.print(df.format(score[0]) + ";");
					System.out.print(df.format(score[1]) + ";");
					System.out.print(df.format(score[2]) + ";");
					if (tdPar.updateMethod == UpdateMethod.IDBD_WK)
						System.out.print(tds.getCommonLR() + ";");
					System.out.println();
				} else {
					System.out.println(i + " ; alpha:"
							+ df.format(tds.getAlpha()) + " ; epsilon: "
							+ df.format(tds.getEpsilon()));

					// Get score
					if (measureStrength) {
						comp.setInitialBoard(new int[7][6]);
						ResultCompSingle scr = comp.compete(pieceNum,
								getValues, true);
						String str = new String();
						if (scr.winner != -1)
							str = str.concat("Simple-Match-Winner: "
									+ scr.agents[scr.winner] + "!!");
						else
							str = str
									.concat("Simple-Match-Winner: No Winner: Draw!!");
						System.out.println(str);
					}
					if (tdPar.evaluate012 || tdPar.evaluateAgent) {
						System.out.println("Start Evaluation...");
						System.out.println(eval.getScoreStr(tds, alphaBetaStd,
								tdPar) + "");
					}
					if (true) {
						System.out
								.println("Average Active Traces per game in last Interval (last "
										+ " games): "
										+ activeEligTracesInterval);// / (float)
																	// interval);
						System.out
								.println("Average Active Traces per game total: "
										+ activeEligTracesOverall / (float) i);
						activeEligTracesInterval = 0;
					}
					if (true) {
						System.out.println(tds.getCommonLR());
					}
					System.out.println("\n");
				}

			}
		}

		// Evaluate Strength
		progress = eval;
		// System.out.println(eval.getScoreStr(tds, alphaBetaStd));
		progress = null;

		// Set N-Tuples in the Options
		Integer nTuples[][] = tds.m_Net.getNTuples1Dim();
		opTD.setNTuples(nTuples);

		// Stop time
		long zstStop = System.currentTimeMillis();
		long time = zstStop - zstStart;
		long seconds = time / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		seconds = seconds % 60;
		minutes = minutes % 60;
		System.out.println("Time needed: " + hours + "h:" + minutes + "min:"
				+ seconds + "s");

		// Print status-message
		c4Buttons.printStatus("[Training finished!]");
		progress = null;
		changeState(State.IDLE);
	}

	/**
	 * Progressbar - Dialog for saving loading the agents
	 * 
	 * @param text
	 *            Additional information to be showed in the
	 * @throws IOException
	 */
	// public JDialog modalDialogProgress(String text) {
	//
	//
	// Thread t1 = new Thread(new Runnable() {
	//
	// @Override
	// public void run() {
	// for (int i = 0; i <= 500; i++) {
	// dpb.setValue(i);
	// // if (dpb.getValue() == 500) {
	// // dlg.setVisible(false);
	// // }
	// try {
	// Thread.sleep(25);
	// } catch (InterruptedException e) {
	// e.printStackTrace();
	// }
	// }
	// }
	// });
	// t1.start();
	// return dlg;
	// }

	// ==============================================================
	// Menu: Load
	// ==============================================================

	// ==============================================================
	// N-Tuple-Operations for
	// Show N-Tuples
	// Set N-Tuples
	// Inspect LUTs
	// ==============================================================
	public Integer[][] getChangedNTuples() {
		Integer[][] tuples = new Integer[nTupleList.size()][];
		for (int i = 0; i < nTupleList.size(); i++) {
			ArrayList<Integer> al = nTupleList.get(i);
			Collections.sort(al);
			tuples[i] = new Integer[al.size()];
			for (int j = 0; j < al.size(); j++)
				tuples[i][j] = al.get(j);
		}
		return tuples;
	}

	private int findInNTuple(int x, ArrayList<Integer> nTuple) {
		for (int i = 0; i < nTuple.size(); i++) {
			int s = nTuple.get(i);
			if (s == x)
				return i;
		}
		return -1;
	}

	private void showSingleTupel(Integer tup[]) {
		resetBoard();
		for (int i = 0; i < tup.length; i++) {
			int x = tup[i] / 6;
			int y = tup[i] % 6;

			int imgIndex = ImgShowComponent.RED;
			ImgShowComponent newImg = replaceImage(playingBoard[x][y],
					imgIndex, x, y);

			if (newImg != playingBoard[x][y]) {
				playingBoardPanel.remove((5 - y) * 7 + x);
				playingBoard[x][y] = newImg;
				playingBoardPanel.add(playingBoard[x][y], (5 - y) * 7 + x);
				playingBoardPanel.invalidate();
				playingBoardPanel.validate();
			}
		}
	}

	private void showSingleTuple(ArrayList<Integer> nTuple) {
		int size = nTuple.size();
		Integer[] nT = new Integer[size];
		for (int i = 0; i < nT.length; i++)
			nT[i] = nTuple.get(i);
		showSingleTupel(nT);
	}

	// ==============================================================
	// Menu: Show N-Tuple. Not used at the moment, since Show / Set
	// N-tuple provides the same functionality
	// ==============================================================
	// private void showNTuples() {
	// int curTupleShow = 0;
	// int player = c4Menu.getSelectedAgent();
	//
	// Agent pa = players[player];
	//
	// if (pa != null && pa.getClass().equals(TDSAgent.class)) {
	// ValueFuncC4 tdsVal = ((TDSAgent) pa).m_Net;
	// Integer tuples[][] = tdsVal.getNTuples1Dim();
	// int numTuples = tdsVal.getNumTuples();
	// showSingleTupel(tuples[curTupleShow]);
	// winNTupleWindow.setNTuples(tdsVal.toString(1));
	// c4Buttons.setWindowPos(winNTupleWindow);
	// while (state == State.SHOWNTUPLE) {
	// switch (action) {
	// case MOVEBACK:
	// if (curTupleShow > 0) {
	// curTupleShow--;
	// showSingleTupel(tuples[curTupleShow]);
	// }
	// action = Action.NOACTION;
	// break;
	// case NEXTMOVE:
	// if (curTupleShow < numTuples - 1) {
	// curTupleShow++;
	// showSingleTupel(tuples[curTupleShow]);
	// }
	// action = Action.NOACTION;
	// break;
	// default:
	// break;
	// }
	// try {
	// Thread.sleep(100);
	// } catch (Exception e) {
	// }
	//
	// }
	// } else
	// c4Buttons.printStatus("[ERROR: No TDS-Agent]");
	//
	// winNTupleWindow.setVisible(false);
	// setInitialBoard();
	// changeState(State.IDLE);
	// }

	// ==============================================================
	// Menu: Set N-Tuples
	// ==============================================================
	private void setNTuples() {
		int player = c4Menu.getSelectedAgent();

		Agent pa = players[player];

		if (pa != null && pa.getClass().equals(TDSAgent.class)) {
			c4Buttons.setWindowPos(winConfigTuples);
			ValueFuncC4 tdsVal = ((TDSAgent) pa).m_Net;
			String tuples[] = new String[tdsVal.getNumTuples()];
			int index = -1;
			Integer nTuples[][] = tdsVal.getNTuples1Dim();

			nTupleList.clear();

			// Add all N-Tuples to a ArrayList
			for (int i = 0; i < nTuples.length; i++) {
				int numPoints = nTuples[i].length;

				// Put single Points of N-Tuple in a List
				ArrayList<Integer> tmpTuple = new ArrayList<Integer>();
				for (int j = 0; j < numPoints; j++)
					tmpTuple.add(nTuples[i][j]);
				Collections.sort(tmpTuple);
				nTupleList.add(tmpTuple);
				tuples[i] = tmpTuple.toString();
			}

			winConfigTuples.setNTuples(tuples);

			while (state == State.SETNTUPLE) {
				int newIndex = winConfigTuples.getSelectedIndex();
				if (newIndex != index || action == Action.DELETE) {
					// load new N-Tuple
					index = newIndex;
					int size = nTupleList.size();
					if (size > 0) {
						showSingleTuple(nTupleList.get(index));
					}
					action = Action.NOACTION;
				}

				if (action == Action.CHANGE) {
					ArrayList<Integer> n = nTupleList.get(index);
					Collections.sort(n);
					winConfigTuples.updateNTuple(n.toString(), index);
					action = Action.NOACTION;
				}

				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}

			}
		} else
			c4Buttons.printStatus("[ERROR: No TDS-Agent]");

		changeState(State.IDLE);
		setInitialBoard();
	}

	// ==============================================================
	// Menu: Inspect LUT
	// ==============================================================
	private void inspNTuples() {
		int player = c4Menu.getSelectedAgent();
		Agent pa = players[player];

		if (pa != null && pa.getClass().equals(TDSAgent.class)) {
			TDSAgent tds = (TDSAgent) pa;
			winLUTShow.setTDSAgent(tds);
			winLUTShow.winTupleList.setVisible(true);
			winLUTShow.setVisible(true);
			while (state == State.INSPNTUPLE) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}
			}
		} else
			c4Buttons.printStatus("[ERROR: No TDS-Agent]");
		changeState(State.IDLE);
		setInitialBoard();
	}

	// ==============================================================
	// Menu: Quick Evaluation
	// ==============================================================
	private void evaluate() {
		// for(int i=0;i<10;i++) {
		int player = c4Menu.getSelectedAgent();
		Agent pa = players[player];
		boolean tdsAgent = pa.getClass().equals(TDSAgent.class);
		boolean abTDSAgent = pa.getClass().equals(AlphaBetaTDSAgent.class);

		if (pa != null && (tdsAgent || abTDSAgent)) {
			TDParams tdPar;
			if (tdsAgent)
				tdPar = ((TDSAgent) pa).getTDParams();
			else
				tdPar = ((AlphaBetaTDSAgent) pa).getTDParams();

			progress = eval;
			c4Buttons.printStatus(eval.getScoreStr(pa, alphaBetaStd, tdPar));
			System.out.println(eval.getTime());
			progress = null;

		} else
			c4Buttons.printStatus("[ERROR: No TDS-Agent or AB-TDS-Agent]");
		// }
		changeState(State.IDLE);
	}

	// ==============================================================
	// Button: Start Game
	// ==============================================================
	private void playGame(boolean newGame) {
		if (newGame) {
			initPlay();
		}
		printValueBar();

		while (state == State.PLAY) {
			// Check for Actions
			handleAction();

			if (players[curPlayer] == null && !gameOver) {

			} else if (!gameOver) {
				c4Buttons.setEnabledPlayStep(players[curPlayer] != null);
				boolean autoMode = c4Buttons.cbAutostep.isSelected();
				if (playStep || autoMode) {
					setPlayStep(false);
					c4Buttons.setEnabledPlayStep(false);

					long startTime = System.currentTimeMillis();
					int x = players[curPlayer].getBestMove(c4.getBoard());

					float timeS = (float) ((System.currentTimeMillis() - startTime) / 1000.0);
					c4Buttons.printStatus("Time needed for move: " + timeS
							+ "s");

					String[] color = { "Yellow", "Red" };
					String sPlayer = players[curPlayer].getName() + " ("
							+ color[curPlayer] + ")";
					makeCompleteMove(x, sPlayer);
					try {
						Thread.sleep(100);
					} catch (Exception e) {
					}

					printValueBar();
				}
			}
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}

		}
		changeState(State.IDLE);
	}

	// ==============================================================
	// Button: Move Back
	// ==============================================================
	private void moveBack() {
		if (!mvList.isEmpty()) {
			gameOver = false;
			int col = mvList.getPrevMove();
			swapPlayer();
			removePiece(col, curPlayer);
			col = mvList.readPrevMove();
			if (col != -1)
				markMove(col, c4.getColHeight(col) - 1, 1 - curPlayer);
			c4Buttons.setEnabledPlayStep(players[curPlayer] != null);
			printValueBar();
		}
	}

	// ==============================================================
	// Button: Next Move
	// ==============================================================
	private void nextMove() {
		if (mvList.isNextMove()) {
			int prevCol = mvList.readPrevMove();
			if (prevCol != -1)
				unMarkMove(prevCol, c4.getColHeight(prevCol) - 1, curPlayer);
			int col = mvList.getNextMove();
			if (c4.canWin(curPlayer + 1, col))
				gameOver = true;
			putPiece(col, curPlayer);
			swapPlayer();
			c4Buttons.setEnabledPlayStep(players[curPlayer] != null);
			printValueBar();
		}

	}

	// ==============================================================
	// Menu: Single Competition
	// ==============================================================
	private void singleCompetition() {
		int first = winCompOptions.getFirstPlayer();
		int second = winCompOptions.getSecondPlayer();
		if (players[first] != null && players[second] != null) {
			progress = comp = new Competition(players[first], players[second],
					alphaBetaStd);
			if (winCompOptions.useCurBoard())
				comp.setInitialBoard(c4.getBoard());
			int pieceNum = winCompOptions.getNumPieces();
			boolean getValues = winCompOptions.logValues();

			ResultCompSingle scr = comp.compete(pieceNum, getValues, false);
			winCompResult.setResult(scr);
			winCompResult.setVisible(true);
			String str = scr.getOverViewResult();
			System.out.print(str);
			progress = null;
		} else
			c4Buttons
					.printStatus("[ERROR: There must be 2 initialized Agents]");
		changeState(State.IDLE);
	}

	// ==============================================================
	// Menu: Multi Competition
	// ==============================================================
	private void multiCompetition() {
		int first = winCompOptions.getFirstPlayer();
		int second = winCompOptions.getSecondPlayer();
		if (players[first] != null && players[second] != null) {
			progress = comp = new Competition(players[first], players[second],
					alphaBetaStd);
			if (winCompOptions.useCurBoard())
				comp.setInitialBoard(c4.getBoard());
			int pieceNum = winCompOptions.getNumPieces();
			boolean getValues = winCompOptions.logValues();
			boolean swapPlayers = winCompOptions.swapPlayers();
			int numGames = winCompOptions.getNumGames();

			ResultCompMulti mcr = comp.multiCompete(numGames, pieceNum,
					swapPlayers, getValues);
			winCompResult.setResult(mcr);
			winCompResult.setVisible(true);
			String str = mcr.getOverViewResult();
			System.out.print(str);
			progress = null;
		} else
			c4Buttons
					.printStatus("[ERROR: There must be 2 initialized Agents]");
		changeState(State.IDLE);
	}

	// ==============================================================
	// Menu: Test Value-Function
	// ==============================================================
	private void testValueFunction() {
		boolean useDatabase = winValueFuncOptions.useDatabase();
		int numPieces = winValueFuncOptions.getNumPieces();
		int numBoards = winValueFuncOptions.getNumBoards();
		int agentNum = winValueFuncOptions.getAgent();
		double deltaCorrect = winValueFuncOptions.getDeltaCorrect();
		double deltaCorrectRange = winValueFuncOptions.getDeltaCorrectRange();
		boolean randomChoiceDB = numPieces < 0;

		if (players[agentNum] != null) {
			ValueFuncTest vft = new ValueFuncTest(players[agentNum], books,
					alphaBetaStd);
			progress = vft;
			vft.setDeltaCorrect(deltaCorrect);
			vft.setDeltaCorrectRange(deltaCorrectRange);

			ResultValueFuncMulti vfrm;
			if (useDatabase)
				vfrm = vft.testDB(numBoards, randomChoiceDB);
			else
				vfrm = vft.test(numBoards, numPieces);
			winCompResult.setResult(vfrm);
			winCompResult.setVisible(true);
			String str = vfrm.getOverViewResult();
			System.out.print(str);
			progress = null;
		} else
			c4Buttons.printStatus("[ERROR: Agent is not initialized]");
		changeState(State.IDLE);

	}

	// ==============================================================
	// Menu: Test Best-Move
	// ==============================================================
	private void testBestMove() {
		int numPieces = winValueFuncOptions.getNumPieces();
		int numBoards = winValueFuncOptions.getNumBoards();
		int agentNum = winValueFuncOptions.getAgent();

		if (players[agentNum] != null) {
			ValueFuncTest vft = new ValueFuncTest(players[agentNum], books,
					alphaBetaStd);
			progress = vft;
			ResultBestMoveMulti bmrm;
			bmrm = vft.bestMoveTest(numBoards, numPieces);
			winCompResult.setResult(bmrm);
			winCompResult.setVisible(true);
			String str = bmrm.getOverViewResult();
			System.out.print(str);
			progress = null;
		} else
			c4Buttons.printStatus("[ERROR: Agent is not initialized]");
		changeState(State.IDLE);

	}

	// ==============================================================
	// Menu: Multi-Training
	// ==============================================================
	private void multiTrain() {
		int tdsPlayer = winMultiTrainOptions.getTDSPlayer();
		int opponent = winMultiTrainOptions.getOpponent();
		Agent pa = players[tdsPlayer];
		Agent ops = players[opponent];

		if (pa != null && pa.getClass().equals(TDSAgent.class) && ops != null) {
			TDSMultiTrain tdsmt = new TDSMultiTrain((TDSAgent) pa, ops,
					alphaBetaStd, books, winMultiTrainOptions, c4.getBoard());
			progress = tdsmt;
			ResultTrainMulti res = tdsmt.startMultiTrain(true, true);
			winCompResult.setResult(res);
			winCompResult.setVisible(true);
			progress = null;
			String str = res.getOverViewResult();
			System.out.print(str);
		} else
			c4Buttons
					.printStatus("[ERROR: Agents are not initialized or no TD-Agent is chosen]");
		changeState(State.IDLE);
	}

	protected void changeState(State st) {
		c4Buttons.enableItems(st);
		state = st;
	}

	public void setPlayStep(boolean value) {
		try {
			mutex.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		playStep = value;
		mutex.release();
	}

	public void run() {
		while (true) {
			// Deactivate most menu-items (except File and Help) for the
			// different states
			c4Menu.setEnabledMenus(new int[] { 1, 2, 3 }, false);
			switch (state) {
			case IDLE:
				c4Menu.setEnabledMenus(new int[] { 1, 2, 3 }, true);
				action = Action.NOACTION;
				break;
			case PLAY:
				// compareTime();
				playGame(true);
				break;
			case COMPETE:
				singleCompetition();
				state = State.IDLE;
				break;
			case MULTICOMPETE:
				multiCompetition();
				state = State.IDLE;
				break;
			case TESTVALUEFUNC:
				testValueFunction();
				state = State.IDLE;
				break;
			case TESTBESTMOVE:
				testBestMove();
				state = State.IDLE;
				break;
			case TRAIN_X:
				if (players[0] instanceof TDSAgent) {
					TDSAgent paX = (TDSAgent) players[0];
					trainTDSAgent(paX, paX.getTDParams().trainGameNum, 0);
				}
				changeState(State.IDLE);
				break;
			case TRAIN_O:
				if (players[1] instanceof TDSAgent) {
					TDSAgent paO = (TDSAgent) players[1];
					trainTDSAgent(paO, paO.getTDParams().trainGameNum, 1);
				}
				changeState(State.IDLE);
				break;
			case TRAIN_EVAL:
				if (players[2] instanceof TDSAgent) {
					TDSAgent paE = (TDSAgent) players[2];
					trainTDSAgent(paE, paE.getTDParams().trainGameNum, 2);
				}
				changeState(State.IDLE);
				break;
			case SHOWNTUPLE:
				// unused, since SETNTUPLE provides the same functionality
				// showNTuples();
				break;
			case SETNTUPLE:
				setNTuples();
				break;
			case INSPNTUPLE:
				inspNTuples();
				break;
			case MULTITRAIN:
				multiTrain();
				break;
			case EVALUATE:
				evaluate();
				break;
			case SAVE_X:
				saveAgent(0);
				break;
			case SAVE_O:
				saveAgent(1);
				break;
			case SAVE_EVAL:
				saveAgent(2);
				break;
			case LOAD_X:
				loadAgent(0);
				break;
			case LOAD_O:
				loadAgent(1);
				break;
			case LOAD_EVAL:
				loadAgent(2);
				break;
			case LOAD_WEIGHTS_X:
				loadAgentWeights(0);
				break;
			case LOAD_WEIGHTS_O:
				loadAgentWeights(1);
				break;
			case LOAD_WEIGHTS_EVAL:
				loadAgentWeights(2);
				break;
			case SAVE_WEIGHTS_X:
				saveAgentWeights(0);
				break;
			case SAVE_WEIGHTS_O:
				saveAgentWeights(1);
				break;
			case SAVE_WEIGHTS_EVAL:
				saveAgentWeights(2);
				break;
			default:
				break;
			}
			handleAction();

			try {
				Thread.sleep(200);
			} catch (Exception e) {
			}
		}
	}

	void saveAgent(int index) {
		Agent td = players[index];
		String str = "";
		if (td instanceof TDSAgent) {
			str = "[Save Agent!]";
			try {
				tdAgentIO.saveTDAgent((TDSAgent) td);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else {
			str = "[No TD-Agent Selected. Nothing saved!]";
		}
		c4Buttons.printStatus(str);
		changeState(State.IDLE);
	}

	void loadAgent(int index) {
		TDSAgent td = tdAgentIO.loadTDAgent();
		String str;
		str = setTDAgent(index, td);
		changeState(State.IDLE);
		c4Buttons.printStatus(str);
	}

	private String setTDAgent(int index, TDSAgent td) {
		String str;
		final String agentTypes[] = { "Agent X", "Agent O", "Agent Eval" };
		if (td == null) {
			str = "No Agent loaded!";
		} else {
			str = "Agent succesfully loaded to " + agentTypes[index] + "!";
			td.m_Net.countActiveWeights(2);
			players[index] = td;
			if (params[index] == null
					|| !params[index].getClass().equals(OptionsTD.class))
				params[index] = new OptionsTD(new TDParams());
			OptionsTD opTD = (OptionsTD) params[index];
			opTD.setTDParams(td.getTDParams());
			switch (index) {
			case 0:
				c4Buttons.cChooseX.setSelectedIndex(2);
				break;
			case 1:
				c4Buttons.cChooseO.setSelectedIndex(2);
				break;
			case 2:
				c4Buttons.cChooseEval.setSelectedIndex(2);
				break;
			}
		}
		return str;
	}

	void saveAgentWeights(int index) {
		Agent td = players[index];
		String str = "";
		if (td instanceof TDSAgent) {
			str = "[Save Agent Weights!]";
			try {
				// JOptionPane
				// .showMessageDialog(
				// c4Frame,
				// "<html><body>The weights of the system will be stored in 2 files, "
				// +
				// "since separate<br> value functions are learnt for both players.<br> "
				// +
				// "Therefore, you have to select two files in the following.<br>"
				// +
				// "For details, refer to the help file.</body></html>");
				tdAgentIO.saveTDAgentWeights2((TDSAgent) td);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else {
			str = "[No TD-Agent Selected. Nothing saved!]";
		}
		c4Buttons.printStatus(str);
		changeState(State.IDLE);
	}

	void loadAgentWeights(int index) {
		TDSAgent td = tdAgentIO.loadTDAgentWeights();
		String str = setTDAgent(index, td);
		changeState(State.IDLE);
		c4Buttons.printStatus(str);
	}

	public void setProgress(Progress p) {
		this.progress = p;
	}

	private void handleAction() {
		switch (action) {
		case MOVEBACK:
			moveBack();
			action = Action.NOACTION;
			break;
		case NEXTMOVE:
			nextMove();
			action = Action.NOACTION;
			break;
		default:
			break;
		}
	}

	private void handleMouseClick(int x, int y) {
		switch (state) {
		case PLAY:
			if (players[curPlayer] == null && !gameOver)
				makeCompleteMove(x, "You");
			break;
		case SETBOARD:
			makeCompleteMove(x, "HuiBuh");
			break;
		case SETNTUPLE:
			int index = winConfigTuples.getSelectedIndex();
			ArrayList<Integer> nTuple = nTupleList.get(index);
			int find = findInNTuple(x * 6 + y, nTuple);
			if (find <= -1) {
				nTuple.add(x * 6 + y);
				putPiece(x, y, 2);
			} else {
				nTuple.remove(find);
				removePiece(x, y, 2);
			}
			action = Action.CHANGE;
			break;
		default:
			break;
		}
	}

	@Override
	public void indexChanged(int newIndex) {
		// not needed in this class yet
	}

	@Override
	public void playerChanged(Player player) {
		// not needed in this class yet
	}

	private class MouseHandler implements MouseListener {
		int x, y;

		MouseHandler(int num1, int num2) {
			x = num1;
			y = num2;
		}

		public void mouseClicked(MouseEvent e) {
		}

		public void mouseEntered(MouseEvent e) {
		}

		public void mouseExited(MouseEvent e) {
		}

		public void mousePressed(MouseEvent e) {
		}

		public void mouseReleased(MouseEvent e) {
		}
	}
}
