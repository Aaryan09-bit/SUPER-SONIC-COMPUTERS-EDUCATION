const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_super_01";
const ADMIN_EMAIL = "aghanghas08@gmail.com";
const TEACHER_UID = "teacher_uid_01";
const STUDENT_A_UID = "student_uid_a";
const STUDENT_B_UID = "student_uid_b";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(db.collection("questions").get());
  await assertFails(db.collection("examinations").get());
  await assertFails(db.collection("exam_answer_keys").doc("exam_1").get());
  await assertFails(db.collection("exam_results").get());
  await assertFails(db.collection("students").get());
  await assertFails(db.collection("teachers").get());
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("questions").get());
  await assertFails(spoofDb.collection("students").get());
});

test("User account creation enforces safe STUDENT role and blocks privilege escalation", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID, {
    email: "studenta@example.com",
    email_verified: true,
  }).firestore();
  const now = pastDate();

  // Reject self-assigned ADMIN role
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).set({
      uid: STUDENT_A_UID,
      email: "studenta@example.com",
      displayName: "Student A",
      role: "ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid student registration succeeds
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_A_UID).set({
      uid: STUDENT_A_UID,
      email: "studenta@example.com",
      displayName: "Student A",
      role: "STUDENT",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field / role escalation update
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      role: "ADMIN",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      isVerified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid displayName update succeeds
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      displayName: "Student A Updated",
      updatedAt: pastDate(1000),
    })
  );
});

test("Admin manages teachers, students, PDF papers, question bank, exams, and answer keys", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const now = pastDate();

  await assertSucceeds(
    adminDb.collection("teachers").doc("tch_01").set({
      teacherCode: "TCH-1001",
      name: "Prof. Sharma",
      email: "sharma@supersonic.edu",
      phone: "9876543210",
      subjectSpecialty: "MS Office & Networking",
      passcode: "PASS1234",
      linkedUid: TEACHER_UID,
      active: true,
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("students").doc("stu_01").set({
      studentCode: "STU-2001",
      name: "Aarav Verma",
      email: "aarav@example.com",
      course: "DCA",
      batch: "Morning-2026",
      assignedTeacherId: "tch_01",
      assignedTeacherUid: TEACHER_UID,
      assignedTeacherName: "Prof. Sharma",
      passcode: "STU1234",
      linkedUid: STUDENT_A_UID,
      active: true,
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("questions").doc("q_01").set({
      questionText: "In MS-Word Ctrl+K is used for?",
      questionType: "MCQ",
      options: ["Copy format", "Delete paragraph", "Insert bookmark", "Create hyperlink"],
      correctAnswers: ["D"],
      subject: "MS Word",
      difficulty: "EASY",
      marks: 2,
      answerApproved: true,
      createdByUid: ADMIN_UID,
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("examinations").doc("exam_01").set({
      title: "MS Word Unit Test 1",
      description: "Official test from Set 01",
      subject: "MS Word",
      batch: "Morning-2026",
      status: "PUBLISHED",
      durationMinutes: 30,
      totalMarks: 2,
      marksPerQuestion: 2,
      negativeMarks: 0.5,
      passingPercentage: 40,
      randomizeQuestions: false,
      randomizeOptions: false,
      showResultImmediately: true,
      allowAnswerReview: true,
      assignedScope: "STUDENTS",
      assignedStudentIds: ["stu_01"],
      assignedStudentUids: [STUDENT_A_UID],
      assignedTeacherUid: TEACHER_UID,
      createdByUid: ADMIN_UID,
      questionCount: 1,
      questions: [
        {
          questionId: "q_01",
          text: "In MS-Word Ctrl+K is used for?",
          questionType: "MCQ",
          options: ["Copy format", "Delete paragraph", "Insert bookmark", "Create hyperlink"],
          marks: 2,
          orderIndex: 0,
        },
      ],
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("exam_answer_keys").doc("exam_01").set({
      examId: "exam_01",
      answers: { q_01: "D" },
      explanations: { q_01: "Ctrl+K opens Insert Hyperlink in MS Word." },
      marksPerQuestion: 2,
      negativeMarks: 0.5,
      passingPercentage: 40,
      createdByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    adminDb.collection("questions").doc("q_future").set({
      questionText: "Future question",
      questionType: "MCQ",
      options: ["A", "B"],
      correctAnswers: ["A"],
      subject: "MS Word",
      difficulty: "EASY",
      marks: 1,
      answerApproved: true,
      createdByUid: ADMIN_UID,
      demoRecord: false,
      createdAt: new Date(Date.now() + 120000),
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Zero-Trust Answer Key Guard, Terminal Attempt Lock & Student Result Isolation", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("examinations").doc("exam_01").set({
      title: "MS Word Test",
      subject: "MS Word",
      status: "PUBLISHED",
      durationMinutes: 20,
      totalMarks: 2,
      marksPerQuestion: 2,
      negativeMarks: 0,
      passingPercentage: 40,
      randomizeQuestions: false,
      randomizeOptions: false,
      showResultImmediately: true,
      allowAnswerReview: true,
      assignedScope: "ALL",
      assignedStudentIds: [],
      assignedStudentUids: [STUDENT_A_UID],
      assignedTeacherUid: TEACHER_UID,
      createdByUid: ADMIN_UID,
      questionCount: 1,
      questions: [{ questionId: "q_01", text: "Q1", questionType: "MCQ", options: ["A", "B"], marks: 2, orderIndex: 0 }],
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("exam_answer_keys").doc("exam_01").set({
      examId: "exam_01",
      answers: { q_01: "A" },
      marksPerQuestion: 2,
      negativeMarks: 0,
      passingPercentage: 40,
      createdByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const stuADb = testEnv.authenticatedContext(STUDENT_A_UID, { email: "a@stu.edu", email_verified: true }).firestore();
  const stuBDb = testEnv.authenticatedContext(STUDENT_B_UID, { email: "b@stu.edu", email_verified: true }).firestore();

  // 1. Student A CANNOT read question bank or answer key BEFORE submitting
  await assertFails(stuADb.collection("questions").get());
  await assertFails(stuADb.collection("exam_answer_keys").doc("exam_01").get());

  // 2. Student A starts attempt (IN_PROGRESS)
  const attemptId = `exam_01_${STUDENT_A_UID}`;
  await assertSucceeds(
    stuADb.collection("exam_attempts").doc(attemptId).set({
      examId: "exam_01",
      examTitle: "MS Word Test",
      studentUid: STUDENT_A_UID,
      studentId: "stu_01",
      studentName: "Aarav Verma",
      assignedTeacherUid: TEACHER_UID,
      status: "IN_PROGRESS",
      responses: {},
      flaggedQuestionIds: [],
      currentQuestionIndex: 0,
      startedAtMillis: Date.now() - 1000,
      timeTakenSeconds: 0,
      demoRecord: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Still CANNOT read answer key while IN_PROGRESS
  await assertFails(stuADb.collection("exam_answer_keys").doc("exam_01").get());

  // 3. Auto-save answer while IN_PROGRESS succeeds
  await assertSucceeds(
    stuADb.collection("exam_attempts").doc(attemptId).update({
      responses: { q_01: "A" },
      currentQuestionIndex: 0,
      timeTakenSeconds: 45,
      updatedAt: pastDate(1000),
    })
  );

  // 4. Submit attempt (IN_PROGRESS -> SUBMITTED)
  await assertSucceeds(
    stuADb.collection("exam_attempts").doc(attemptId).update({
      responses: { q_01: "A" },
      flaggedQuestionIds: [],
      currentQuestionIndex: 0,
      status: "SUBMITTED",
      submittedAtMillis: Date.now(),
      timeTakenSeconds: 60,
      updatedAt: pastDate(500),
    })
  );

  // 5. Terminal State Lock: Student CANNOT modify attempt after SUBMITTED
  await assertFails(
    stuADb.collection("exam_attempts").doc(attemptId).update({
      responses: { q_01: "B" },
      updatedAt: pastDate(100),
    })
  );

  // 6. Now Student A CAN read exam_answer_keys to evaluate & create exam_results
  await assertSucceeds(stuADb.collection("exam_answer_keys").doc("exam_01").get());

  // Student B still CANNOT read exam_answer_keys because Student B hasn't submitted
  await assertFails(stuBDb.collection("exam_answer_keys").doc("exam_01").get());

  await assertSucceeds(
    stuADb.collection("exam_results").doc(attemptId).set({
      examId: "exam_01",
      examTitle: "MS Word Test",
      subject: "MS Word",
      batch: "Morning-2026",
      studentUid: STUDENT_A_UID,
      studentId: "stu_01",
      studentCode: "STU-2001",
      studentName: "Aarav Verma",
      assignedTeacherId: "tch_01",
      assignedTeacherUid: TEACHER_UID,
      assignedTeacherName: "Prof. Sharma",
      totalQuestions: 1,
      correctCount: 1,
      wrongCount: 0,
      unattemptedCount: 0,
      pendingManualCount: 0,
      totalMarks: 2,
      obtainedMarks: 2,
      percentage: 100,
      passed: true,
      timeTakenSeconds: 60,
      resultsVisibleToStudent: true,
      allowAnswerReview: true,
      gradingStatus: "FINALIZED",
      questionAnalyses: [
        {
          questionId: "q_01",
          questionText: "Q1",
          questionType: "MCQ",
          options: ["A", "B"],
          studentAnswer: "A",
          correctAnswer: "A",
          status: "CORRECT",
          marksObtained: 2,
          maxMarks: 2,
          needsManualReview: false,
        },
      ],
      demoRecord: false,
      submittedAt: now,
      updatedAt: now,
    })
  );

  // 7. Student Isolation: Student B CANNOT view Student A's result or list unfiltered results
  await assertFails(stuBDb.collection("exam_results").doc(attemptId).get());
  await assertFails(stuBDb.collection("exam_results").get());
  await assertSucceeds(stuADb.collection("exam_results").where("studentUid", "==", STUDENT_A_UID).get());

  // 8. Teacher Scoping: Assigned teacher can view result, CANNOT modify FINALIZED result
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "sharma@supersonic.edu", email_verified: true }).firestore();
  await assertSucceeds(teacherDb.collection("exam_results").where("assignedTeacherUid", "==", TEACHER_UID).get());
  await assertFails(
    teacherDb.collection("exam_results").doc(attemptId).update({
      obtainedMarks: 0,
      updatedAt: pastDate(100),
    })
  );
});

test("Teacher can grade PENDING_REVIEW subjective results for assigned students only", async () => {
  const now = pastDate();
  const resId = `exam_subj_${STUDENT_A_UID}`;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("exam_results").doc(resId).set({
      examId: "exam_subj",
      examTitle: "Short Answer Quiz",
      subject: "Computer Fundamentals",
      studentUid: STUDENT_A_UID,
      studentName: "Aarav Verma",
      assignedTeacherUid: TEACHER_UID,
      totalQuestions: 1,
      correctCount: 0,
      wrongCount: 0,
      unattemptedCount: 0,
      pendingManualCount: 1,
      totalMarks: 5,
      obtainedMarks: 0,
      percentage: 0,
      passed: false,
      timeTakenSeconds: 120,
      resultsVisibleToStudent: true,
      allowAnswerReview: true,
      gradingStatus: "PENDING_REVIEW",
      questionAnalyses: [
        {
          questionId: "q_s1",
          questionText: "Explain RAM",
          questionType: "SHORT_ANSWER",
          options: [],
          studentAnswer: "Random Access Memory",
          correctAnswer: "Volatile main memory",
          status: "PENDING",
          marksObtained: 0,
          maxMarks: 5,
          needsManualReview: true,
        },
      ],
      demoRecord: false,
      submittedAt: now,
      updatedAt: now,
    });
  });

  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "sharma@supersonic.edu", email_verified: true }).firestore();
  await assertSucceeds(
    teacherDb.collection("exam_results").doc(resId).update({
      correctCount: 1,
      wrongCount: 0,
      pendingManualCount: 0,
      obtainedMarks: 5,
      percentage: 100,
      passed: true,
      gradingStatus: "FINALIZED",
      questionAnalyses: [
        {
          questionId: "q_s1",
          questionText: "Explain RAM",
          questionType: "SHORT_ANSWER",
          options: [],
          studentAnswer: "Random Access Memory",
          correctAnswer: "Volatile main memory",
          status: "CORRECT",
          marksObtained: 5,
          maxMarks: 5,
          needsManualReview: false,
        },
      ],
      updatedAt: pastDate(100),
    })
  );

  // Once FINALIZED, teacher can no longer edit it
  await assertFails(
    teacherDb.collection("exam_results").doc(resId).update({
      obtainedMarks: 2,
      updatedAt: pastDate(50),
    })
  );
});
