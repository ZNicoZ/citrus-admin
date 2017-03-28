import {Component, OnInit} from '@angular/core';
import {Router, ActivatedRoute, NavigationStart} from '@angular/router';
import {TestGroup, Test} from "../../model/tests";
import {Alert} from "../../model/alert";
import {AlertService} from "../../service/alert.service";
import * as jQueryVar from 'jquery'
import {TestStateActions, TestStateService} from "./test.state";
import {Observable} from "rxjs";
import {RouterState} from "../../service/router-state.service";

declare var jQuery:typeof jQueryVar;

@Component({
    host: {
       '(document:keyup)': 'handleKeyUp($event)',
    },
    templateUrl: 'test-editor.html'
})
export class TestEditorComponent implements OnInit {

    constructor(
                private alertService: AlertService,
                private router: Router,
                private testActions:TestStateActions,
                private testState:TestStateService,
                private route:ActivatedRoute,
                private routerState:RouterState
    ) {
    }

    openTests: Observable<Test[]>;
    packages:Observable<TestGroup[]>;
    selectedTest: Observable<Test>;

    ngOnInit() {
        this.packages = this.testState.packages;
        this.openTests = this.testState.openTabs;
        this.selectedTest = this.testState.selectedTest;

        /** Navigate to the main page if we dont have opentabs in state **/
        this.testState
            .openTabs
            .filter(ot => ot.length === 0)
            .subscribe(() => this.router.navigate(['tests', 'detail']));

        /** Navigate to a test route if selected tab is changed **/
        this.testState.selectedTest.filter(t => t != null).subscribe(t => {
            this.router.navigate([t.name])
        })

        Observable.combineLatest(
            this.routerState.path.filter(p => p === '/tests/detail').take(1),
            this.testState.selectedTest.filter(t => t != null).take(1)
        )
        .subscribe(([u, t]) => this.router.navigate(['tests','detail', t.name]))

    }

    onTabClosed(test:Test) {
        this.testActions.removeTab(test);
    }

    onTabSelected(test:Test) {
        this.testActions.selectTest(test);
        this.navigateToTestInfo(test);
    }

    openTestList() {
        (jQuery('#dialog-list-tests') as any).modal();
    }

    closeTestList() {
        (jQuery('#dialog-list-tests') as any).modal('hide');
    }

    open(test: Test) {
        this.testActions.addTab(test);
        this.navigateToTestInfo(test)
    }

    handleKeyUp(event:KeyboardEvent) {
        if (event && event.key.toUpperCase() == "O") {
            this.openTestList();
        }
    }

    notifyError(error: any) {
        this.alertService.add(new Alert("danger", error.message, false));
    }

    private navigateToTestInfo(test:Test) {
        this.router.navigate(['/tests', 'detail', test.name, 'info']);
    }
}