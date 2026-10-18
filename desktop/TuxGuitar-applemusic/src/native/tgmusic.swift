// Helper that lets TuxGuitar drive the macOS Music app.
//
// It reads one command per line from its standard input and answers with one line, keeping the
// compiled AppleScripts alive: starting "osascript" per command costs about 140 ms, while a
// command sent to this process is answered within a few milliseconds, which is what makes it
// possible to measure where the music really is.
//
// Commands: pos | state | track | play | pause | seek <seconds> | quit

import Foundation

final class MusicBridge {

	private let position = NSAppleScript(source: "tell application \"Music\" to get player position")!
	private let state = NSAppleScript(source: "tell application \"Music\" to get player state as text")!
	private let play = NSAppleScript(source: "tell application \"Music\" to play")!
	private let pause = NSAppleScript(source: "tell application \"Music\" to pause")!
	private let track = NSAppleScript(source:
		"tell application \"Music\" to return ((duration of current track) as text) & \"|\" & ((name of current track) as text)")!

	private func run(_ script: NSAppleScript) -> (NSAppleEventDescriptor?, String?) {
		var error: NSDictionary?
		let result = script.executeAndReturnError(&error)
		if let error = error {
			return (nil, (error[NSAppleScript.errorMessage] as? String) ?? "apple event failed")
		}
		return (result, nil)
	}

	func handle(_ line: String) -> String {
		let parts = line.split(separator: " ", maxSplits: 1).map(String.init)
		let command = parts.first ?? ""

		switch command {
		case "pos":
			let (result, error) = run(self.position)
			return error.map { "err \($0)" } ?? String(format: "pos %.4f", result!.doubleValue)
		case "state":
			let (result, error) = run(self.state)
			return error.map { "err \($0)" } ?? "state \(result!.stringValue ?? "unknown")"
		case "track":
			let (result, error) = run(self.track)
			return error.map { "err \($0)" } ?? "track \(result!.stringValue ?? "")"
		case "play":
			let (_, error) = run(self.play)
			return error.map { "err \($0)" } ?? "ok"
		case "pause":
			let (_, error) = run(self.pause)
			return error.map { "err \($0)" } ?? "ok"
		case "seek":
			guard parts.count == 2, let seconds = Double(parts[1]) else {
				return "err seek needs a number"
			}
			// a new script each time: the position is part of the source
			let (_, error) = run(NSAppleScript(source:
				"tell application \"Music\" to set player position to \(seconds)")!)
			return error.map { "err \($0)" } ?? "ok"
		default:
			return "err unknown command"
		}
	}
}

setvbuf(stdout, nil, _IOLBF, 0)

let bridge = MusicBridge()
while let line = readLine(strippingNewline: true) {
	if line == "quit" {
		break
	}
	print(bridge.handle(line))
}
