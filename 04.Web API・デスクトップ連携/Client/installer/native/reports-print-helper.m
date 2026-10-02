#import <Cocoa/Cocoa.h>
#import <PDFKit/PDFKit.h>
@interface ReportPages:NSView
@property PDFDocument *document;
@end
@implementation ReportPages
-(BOOL)knowsPageRange:(NSRangePointer)range{*range=NSMakeRange(1,self.document.pageCount);return YES;}
-(NSRect)rectForPage:(NSInteger)n{return [[self.document pageAtIndex:n-1]boundsForBox:kPDFDisplayBoxMediaBox];}
-(NSPoint)locationOfPrintRect:(NSRect)rect{return NSZeroPoint;}
-(void)drawRect:(NSRect)rect{NSInteger n=NSPrintOperation.currentOperation.currentPage;[[self.document pageAtIndex:n-1]drawWithBox:kPDFDisplayBoxMediaBox toContext:NSGraphicsContext.currentContext.CGContext];}
@end
int main(int argc,const char **argv){@autoreleasepool{
 if(argc!=2)return 2;[NSApplication sharedApplication];[NSApp setActivationPolicy:NSApplicationActivationPolicyAccessory];[NSApp finishLaunching];
 PDFDocument *document=[[PDFDocument alloc]initWithURL:[NSURL fileURLWithPath:@(argv[1])]];if(!document||document.pageCount<1)return 3;
 NSRect media=[[document pageAtIndex:0]boundsForBox:kPDFDisplayBoxMediaBox];BOOL landscape=media.size.width>media.size.height;NSPrintInfo *info=NSPrintInfo.sharedPrintInfo.copy;
 info.paperSize=landscape?NSMakeSize(media.size.height,media.size.width):media.size;info.orientation=landscape?NSPaperOrientationLandscape:NSPaperOrientationPortrait;
 info.leftMargin=info.rightMargin=info.topMargin=info.bottomMargin=0;info.horizontallyCentered=info.verticallyCentered=NO;
 ReportPages *view=[[ReportPages alloc]initWithFrame:media];view.document=document;NSPrintOperation *operation=[NSPrintOperation printOperationWithView:view printInfo:info];
 operation.showsPrintPanel=YES;operation.showsProgressPanel=YES;operation.printPanel.options|=NSPrintPanelShowsPreview|NSPrintPanelShowsCopies|NSPrintPanelShowsPageRange|NSPrintPanelShowsPaperSize|NSPrintPanelShowsOrientation;
 [NSApp activateIgnoringOtherApps:YES];BOOL printed=[operation runOperation];fprintf(stdout,"%s\n",printed?"REPORTS_PRINTED":"REPORTS_CANCELLED");return 0;
}}
