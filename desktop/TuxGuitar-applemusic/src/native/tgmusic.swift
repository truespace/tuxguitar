// Helper that lets TuxGuitar drive the macOS Music app.
//
// It reads one command per line from its standard input and answers with one line, keeping the
// compiled AppleScripts alive: starting "osascript" per command costs about 140 ms, while a
// command sent to this process is answered within a few milliseconds, which is what makes it
// possible to measure where the music really is.
//
// Commands: pos | state | track | select <name> | play | pause | seek <seconds> | quit

import Foundation

final class MusicBridge {

	private let position = NSAppleScript(source: "tell application \"Music\" to get player position")!
	private let state = NSAppleScript(source: "tell application \"Music\" to get player state as text")!
	private let play = NSAppleScript(source: "tell application \"Music\" to play")!
	private let pause = NSAppleScript(source: "tell application \"Music\" to pause")!
	// a track that is still being loaded answers neither, so both are asked for separately
	private let track = NSAppleScript(source: """
		tell application "Music"
			set theDuration to 0
			set theName to ""
			try
				set theDuration to (duration of current track)
			end try
			try
				set theName to (name of current track)
			end try
			return ((theDuration as real) as text) & "|" & (theName as text)
		end tell
		""")!

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
		case "select":
			guard parts.count == 2 else {
				return "err select needs a name"
			}
			// loading a track means playing it; it is paused again at once, before it is heard
			let name = parts[1].replacingOccurrences(of: "\"", with: "")
			let (result, error) = run(NSAppleScript(source: """
				tell application "Music"
					set matches to (every track of library playlist 1 whose name is "\(name)")
					if matches is {} then
						set matches to (every track of library playlist 1 whose name contains "\(name)")
					end if
					if matches is {} then return "notfound"
					play (item 1 of matches)
					-- loading a track means playing it, and it is not ready to be moved yet:
					-- the synchronisation seeks to where the score starts anyway
					try
						pause
					end try
					return "found"
				end tell
				""")!)
			if let error = error {
				return "err \(error)"
			}
			return (result!.stringValue == "found" ? "ok" : "err no track named \(name)")
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
