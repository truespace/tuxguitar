/*
 * "tuxguitar:" links on iOS.
 *
 * The URL of such a link reaches the application delegate, which belongs to the Gluon runtime:
 * its class is only known once it has been set, so the opening method is added to it from a hook
 * on -[UIApplication setDelegate:]. Links are queued here and picked up by TGGluonUrlScheme.
 *
 * On macOS nothing is registered and tg_url_take() always reports an empty queue.
 */

#import <TargetConditionals.h>
#include <string.h>

#if TARGET_OS_IPHONE

#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <pthread.h>

static NSMutableArray<NSString *> *tg_urls = nil;
static pthread_mutex_t tg_url_mutex = PTHREAD_MUTEX_INITIALIZER;

static void tg_url_queue(NSURL *url) {
	if (url == nil) {
		return;
	}
	pthread_mutex_lock(&tg_url_mutex);
	if (tg_urls == nil) {
		tg_urls = [[NSMutableArray alloc] init];
	}
	[tg_urls addObject:[url absoluteString]];
	pthread_mutex_unlock(&tg_url_mutex);
}

static BOOL tg_url_open(id self, SEL _cmd, UIApplication *application, NSURL *url, NSDictionary *options) {
	tg_url_queue(url);
	return YES;
}

// the delegate of the Gluon runtime may implement the method itself: call it as well
static BOOL tg_url_open_chained(id self, SEL _cmd, UIApplication *application, NSURL *url, NSDictionary *options) {
	tg_url_queue(url);

	SEL original = NSSelectorFromString(@"tg_original_application:openURL:options:");
	if ([self respondsToSelector:original]) {
		BOOL (*implementation)(id, SEL, UIApplication *, NSURL *, NSDictionary *) =
			(BOOL (*)(id, SEL, UIApplication *, NSURL *, NSDictionary *)) [self methodForSelector:original];
		implementation(self, original, application, url, options);
	}
	return YES;
}

static void tg_url_hook_delegate(id delegate) {
	if (delegate == nil) {
		return;
	}

	Class delegateClass = object_getClass(delegate);
	SEL opening = @selector(application:openURL:options:);
	Method existing = class_getInstanceMethod(delegateClass, opening);
	const char *types = "B@:@@@";

	if (existing == NULL) {
		class_addMethod(delegateClass, opening, (IMP) tg_url_open, types);
	} else {
		// keep the original under another name, so that it still runs
		class_addMethod(delegateClass, NSSelectorFromString(@"tg_original_application:openURL:options:"),
			method_getImplementation(existing), types);
		method_setImplementation(existing, (IMP) tg_url_open_chained);
	}
}

@interface TGUrlHook : NSObject
@end

@implementation TGUrlHook

static void (*tg_url_set_delegate)(id, SEL, id) = NULL;

static void tg_url_setDelegate(id self, SEL _cmd, id delegate) {
	tg_url_hook_delegate(delegate);
	if (tg_url_set_delegate != NULL) {
		tg_url_set_delegate(self, _cmd, delegate);
	}
}

+ (void) load {
	Method method = class_getInstanceMethod([UIApplication class], @selector(setDelegate:));
	if (method != NULL) {
		tg_url_set_delegate = (void (*)(id, SEL, id)) method_getImplementation(method);
		method_setImplementation(method, (IMP) tg_url_setDelegate);
	}

	// a link that started the application is handed over as a launch option
	[[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidFinishLaunchingNotification
		object:nil queue:nil usingBlock:^(NSNotification *notification) {
			tg_url_queue([[notification userInfo] objectForKey:UIApplicationLaunchOptionsURLKey]);
		}];
}

@end

/* Copies the next pending link into buffer as a NUL terminated UTF-8 string, and returns its
   length, or 0 when nothing is pending (or the link does not fit into the buffer). */
int tg_url_take(char *buffer, int size) {
	int length = 0;

	pthread_mutex_lock(&tg_url_mutex);
	if (tg_urls != nil && [tg_urls count] > 0) {
		const char *url = [[tg_urls objectAtIndex:0] UTF8String];
		int urlLength = (url != NULL ? (int) strlen(url) : 0);
		if (urlLength > 0 && urlLength < size) {
			memcpy(buffer, url, urlLength + 1);
			length = urlLength;
		}
		[tg_urls removeObjectAtIndex:0];
	}
	pthread_mutex_unlock(&tg_url_mutex);

	return length;
}

#else

int tg_url_take(char *buffer, int size) {
	return 0;
}

#endif
