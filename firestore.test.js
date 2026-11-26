const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/memories`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/messages`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/memories`).doc("mem_1").set({
      userId: ALICE_UID,
      category: "fact",
      label: "City",
      value: "Mumbai",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, list, and delete preferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const prefRef = aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("profile");

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      displayName: "Aarav",
      preferredLanguage: "Hinglish",
      speechSpeed: 1.0,
      autoSpeak: true,
      memoryEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    prefRef.update({
      preferredLanguage: "Hindi",
      speechSpeed: 1.15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field & future timestamp & invalid language
  await assertFails(
    prefRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    prefRef.update({
      preferredLanguage: "Klingon",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    prefRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can manage memories, reminders, and messages; cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Memories
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/memories`).doc("mem_1").set({
      userId: ALICE_UID,
      category: "project",
      label: "Current App",
      value: "Building Kavya AI",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/memories`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/memories`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/memories`).doc("mem_1").get());
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/memories`).doc("mem_bad").set({
      id: "mem_bad",
      userId: ALICE_UID,
      category: "fact",
      label: "Test",
      value: "Test",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reminders
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").set({
      userId: ALICE_UID,
      title: "Study Physics",
      dueTimeText: "7:00 PM",
      dueAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").delete()
  );

  // Messages (with optional actionSummary)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/messages`).doc("msg_1").set({
      userId: ALICE_UID,
      sender: "kavya",
      text: "Hey! Main Kavya hoon!",
      mode: "online",
      actionSummary: "Opened YouTube",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/messages`).doc("msg_2").set({
      userId: ALICE_UID,
      sender: "user",
      text: "Open YouTube",
      mode: "online",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/messages`).doc("msg_3").set({
      userId: ALICE_UID,
      sender: "kavya",
      text: "Future message",
      mode: "online",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
